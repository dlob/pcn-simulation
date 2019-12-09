package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetworkException
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random

class MDARTRouter (
        self: Peer,
        override val socket: IPSocket,
        override val strategy: Strategy,
        initialKnownPeers: Map<WalletAddress, Peer>,
        initialKnownChannels: Set<StaticChannelInformation>,
        private val addrSize: Int,
        private val refreshInterval: Int,
        private val ttl: Int,
        cacheSize: Int
) : AbstractRouter(self, initialKnownPeers, initialKnownChannels, cacheSize) {

    companion object {
        val log by logger()
    }

    private var refreshCounter = Random(self.walletAddress.hashCode()).nextInt(refreshInterval) // ensure the refresh cycles are deterministically distributed among the interval
    private val state = NodeState(-1, self.walletAddress, mutableMapOf(), mutableMapOf(), 0)
    private val lookupTable = mutableMapOf<WalletAddress, LookupTableEntry>()

    init {
        socket.registerReceiveCallback { packet ->
            when (val message = packet.payload) {
                is FindRouteRequest -> {
                    val lastNeighbor = neighbors.values.single { it.peer.ipAddress == packet.sender }
                    val response = findRoute(message.destinationAddress, message.ttl, message.excludePeers, lastNeighbor)
                    socket.respond(packet.sender, packet.id, response)
                }
                is DHTRequest -> {
                    val response = requestDHT(message.anchorAddress, message.identifier, message.address, message.peer, message.ttl)
                    socket.respond(packet.sender, packet.id, response)
                }
                is NeighborUpdateNotification -> {
                    state.neighborUpdates[message.update.identifier] = message.update
                    updateRoutingTable()
                }
            }
        }
    }

    override suspend fun findRoutes(target: WalletAddress): Set<Route> {
        if (state.address < 0) {
            log.warn("No routing address present for ${self.walletAddress}.")
            return setOf()
        }

        // Lookup the target's routing address in the DHT
        val anchorAddress = hashIdentifier(target)
        val dhtResponse = requestDHT(anchorAddress, target)
        if (dhtResponse.failed) {
            return setOf()
        }

        if (dhtResponse.address == state.address) {
            log.warn("Routing address of target matches with own address ${state.address.toAddressString()}.")
            return setOf()
        }

        // Determine routes
        val routes = mutableSetOf<Route>()
        for(i in 0 until ttl) {
            val excludePeers = routes.flatMap { it.peers.keys }.distinct().minus(target).toList()
            val routeResponse = findRoute(dhtResponse.address, ttl, excludePeers)
            if (routeResponse.failed) break
            else routes.add(routeResponse.route!!)
        }
        return routes
    }

    override suspend fun onCycle() {
        // Remove old entries in the neighbor updates
        for (entry in state.neighborUpdates.entries.toList()) {
            if (entry.value.lastUpdate + (1.5 * refreshInterval) < currentCycle) {
                state.neighborUpdates.remove(entry.key)
            }
        }
        // Remove old entries in the address lookup table
        for (entry in lookupTable.entries.toList()) {
            if (entry.value.lastUpdate + (1.5 * refreshInterval) < currentCycle) {
                lookupTable.remove(entry.key)
            }
        }

        updateRoutingTable()

        if (refreshCounter <= 0) {
            refreshCounter = refreshInterval

            // Select a new routing address if current is invalid
            if (!validateAddress()) {
                selectAddress()
                updateRoutingTable()
            }

            // Only continue with valid address
            if (state.address >= 0) {
                // Send updated routing table to the neighbors
                sendUpdateNotification()
                // Send current routing address to anchor node (considering the updated routing table)
                storeAddressDHT()
            }
        }
        refreshCounter--
    }

    override suspend fun onChannelRemoved(peer: WalletAddress) {
        // Remove neighbor from neighborUpdates and routing table
        state.neighborUpdates.remove(peer)
        updateRoutingTable()
        // Refresh routing table on next cycle
        refreshCounter = 0
    }

    private suspend fun findRoute(destinationAddress: Int, ttl: Int, excludePeers: List<WalletAddress>, lastNeighbor: Neighbor? = null): FindRouteResponse {
        // If the destination is reached, respond with route
        if (destinationAddress == state.address && lastNeighbor != null) {
            val channelInfo = strategy.information.disclose(lastNeighbor.channel.toDynamicChannelInformation(lastNeighbor.peer.walletAddress, ChannelFee.ZERO))
            val route = Route(listOf(channelInfo), mapOf(Pair(self.walletAddress, self)))
            return FindRouteResponse(self.walletAddress, route)
        }

        // If ttl is zero or lower, respond  with failure
        if (ttl <= 0) {
            log.warn("TTL used up for FindRoutesRequest to ${destinationAddress.toAddressString()} at ${self.walletAddress}")
            return FindRouteResponse("", null, true)
        }

        // Iterate the levels of the address tree to find a suitable next node
        val diffLevel = levelSibling(destinationAddress, state.address)
        var bestRoute: RoutingTableEntry? = null
        for (i in diffLevel until addrSize) {
            if (!state.routingTable.containsKey(i)) continue
            bestRoute = state.routingTable.getValue(i)
                    .filter { !excludePeers.contains(it.nextHopIdentifier) }
                    .sortedWith(compareBy<RoutingTableEntry> { levelSibling(destinationAddress, it.nextHop) }.thenBy { it.cost })
                    .firstOrNull()
            if (bestRoute != null) break
        }
        if (bestRoute == null) {
            // No route found
            return FindRouteResponse("", null, true)
        }

        // Forward the findRoute request
        val nextHopIdentifier = bestRoute.nextHopIdentifier
        val nextHopIpAddress = neighbors.getValue(bestRoute.nextHopIdentifier).peer.ipAddress
        val response = try {
            socket.request(nextHopIpAddress, FindRouteRequest(destinationAddress, ttl - 1, excludePeers), 500) as FindRouteResponse
        } catch (e: IPNetworkException) {
            log.debug("Sending FindRoutesRequest from ${self.walletAddress} to $nextHopIdentifier failed.", e)
            FindRouteResponse("", null, true)
        }
        return if (!response.failed && response.route != null && lastNeighbor != null) {
            response.route.peers.values.forEach { addToPeerCache(it) }
            val fee = strategy.fee.determine(nextHopIdentifier)
            val channelInfo = strategy.information.disclose(lastNeighbor.channel.toDynamicChannelInformation(lastNeighbor.peer.walletAddress, fee))
            response.copy(route = response.route.copy(
                    channels = listOf(channelInfo) + response.route.channels,
                    peers = response.route.peers.plus(Pair(self.walletAddress, self))
            ))
        } else {
            response
        }
    }

    private suspend fun requestDHT(anchorAddress: Int, identifier: WalletAddress, address: Int = -1, peer: Peer? = null, ttl: Int = this.ttl): DHTResponse {
        if (peer != null) addToPeerCache(peer)
        // Return the address if the node knows it by chance
        if (peer == null && lookupTable.containsKey(identifier)) {
            val l = lookupTable.getValue(identifier)
            return DHTResponse(l.address, l.peer)
        }
        // If ttl is zero or lower, respond  with failure
        if (ttl <= 0 ) {
            log.warn("TTL used up for DHTRequest to ${anchorAddress.toAddressString()} at ${self.walletAddress}")
            return DHTResponse(-1, null, true)
        }

        // Find the a valid next hop in the routing table to forward the DHT request
        // Since not all addresses are used, a strategy is used to fill the gaps
        var diffLevel = levelSibling(anchorAddress, state.address)
        var modAnchorAddress = anchorAddress
        // Strategy: if a matching sibling is not found, the level is ignored (DART)
        while (diffLevel >= 0 && (!state.routingTable.containsKey(diffLevel) || state.routingTable.getValue(diffLevel).size == 0)) {
            // level was not successful, hence toggle the current bit to reduce the diffLevel to the next less significant bit
            modAnchorAddress = modAnchorAddress.toggleBit(diffLevel--)
        }
        /*
        // Alternative strategy: zero lower bits (M-DART in NS-2) (performs not as good as the strategy of DART)
        for (i in 0 until addrSize) {
            if (diffLevel < 0) break
            if (state.routingTable.containsKey(diffLevel) && state.routingTable.getValue(diffLevel).size > 0) break
            modAnchorAddress = modAnchorAddress.clearBit(i)
            diffLevel = levelSibling(anchorAddress, state.address)
        }
        if (!state.routingTable.containsKey(diffLevel) || state.routingTable.getValue(diffLevel).size == 0) {
            modAnchorAddress = state.address
            diffLevel = -1
        }
        */

        // The node itself is the anchor node
        if (diffLevel == -1) {
            // If a peer is set, the request wants the node to store the address.
            if (peer != null) {
                lookupTable[identifier] = LookupTableEntry(address, peer, currentCycle)
            }
            val l = lookupTable[identifier]
            return if (l == null) {
                DHTResponse(-1, null, true)
            } else {
                DHTResponse(l.address, l.peer)
            }
        }

        // Find the best route: nearest sibling and lowest cost
        val bestRoute = state.routingTable.getValue(diffLevel)
                .sortedWith(compareBy<RoutingTableEntry> { levelSibling(modAnchorAddress, it.nextHop) }.thenBy { it.cost })
                .first()
        // Try forwarding the DHT request
        val nextHopIpAddress = neighbors.getValue(bestRoute.nextHopIdentifier).peer.ipAddress
        val response = try {
            socket.request(nextHopIpAddress, DHTRequest(modAnchorAddress, identifier, address, peer, ttl - 1), 500) as DHTResponse
        } catch (e: IPNetworkException) {
            DHTResponse(-1, null, true)
        }
        if (response.peer != null) addToPeerCache(response.peer)
        return response
    }

    data class FindRouteRequest(
            val destinationAddress: Int,
            val ttl: Int,
            val excludePeers: List<WalletAddress>
    )

    data class FindRouteResponse(
            val destinationIdentifier: WalletAddress,
            val route: Route?,
            val failed: Boolean = false
    )

    data class DHTRequest(
            val anchorAddress: Int,
            val identifier: WalletAddress,
            val address: Int,
            val peer: Peer?,
            val ttl: Int
    )

    data class DHTResponse(
            val address: Int,
            val peer: Peer?,
            val failed: Boolean = false
    )

    data class NeighborUpdateNotification(
            val update: NeighborUpdate
    )

    /**
     * Obtain a routing address from an identifier.
     * This is done by hashing the identifier with sha-256 and using [addrSize] first bits.
     */
    private fun hashIdentifier(id: WalletAddress): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        val idHash = digest.digest(id.toByteArray())
        val wrapped = ByteBuffer.wrap(idHash)
        val addr = wrapped.int
        // mask bits with 0 that are of higher order than [addrSize]
        return addr and ((1 shl addrSize) - 1)
    }

    /**
     * Send current routing table to all neighbors
     */
    private suspend fun sendUpdateNotification() {
        val update = NeighborUpdateNotification(NeighborUpdate(
                state.address,
                state.identifier,
                state.routingTable.map {
                    val entry = it.value.reduce { e1, e2 ->
                        if (e1.minId < e2.minId || (e1.minId == e2.minId && e1.cost < e2.cost)) e1 else e2
                    }
                    Pair(it.key, NeighborUpdateEntry(entry.cost, entry.minId, entry.routeLog))
                }.toMap(),
                state.lastUpdate
        ))
        for (neighbor in neighbors.values) {
            try {
                socket.notify(neighbor.peer.ipAddress, update)
            } catch (e: IPNetworkException) {
                log.warn("Notifying neighbor ${neighbor.peer.walletAddress} from ${self.walletAddress} with updated routing table failed.", e)
            }
        }
    }

    /**
     * Store the current address in the DHT
     */
    private suspend fun storeAddressDHT() {
        val anchorAddress = hashIdentifier(self.walletAddress)
        val dhtResponse = requestDHT(anchorAddress, self.walletAddress, state.address, self)
        if (dhtResponse.failed) {
            log.warn("Storing the routing address of ${self.walletAddress} in the DHT failed.")
        }
    }

    /**
     * Update the routing table based on the received neighbor updates.
     */
    private fun updateRoutingTable() {
        // Remove old entries
        state.routingTable.clear()
        // Populate with new entries if node has a valid address
        if (state.address >= 0) {
            for (neighbor in state.neighborUpdates.values) {
                populateRoutingTable(neighbor)
            }
        }
        state.lastUpdate = currentCycle
    }

    /**
     * Populating the routing table with updates from a neighbor.
     */
    private fun populateRoutingTable(neighbor: NeighborUpdate) {
        val diffLevel = levelSibling(state.address, neighbor.address)
        if (diffLevel == -1) return

        if (levelId(neighbor, diffLevel - 1) <= minId(diffLevel)) {
            var entryPresent = false
            val diffLevelRoutes = state.routingTable[diffLevel] ?: mutableListOf()
            for (route in diffLevelRoutes) {
                if (route.nextHop == neighbor.address && levelId(neighbor, diffLevel - 1) >= minId(diffLevel)) {
                    entryPresent = true
                }
            }
            if (!entryPresent) {
                diffLevelRoutes.add(RoutingTableEntry(
                        neighbor.address,
                        neighbor.identifier,
                        1,
                        levelId(neighbor, diffLevel - 1),
                        0.setBit(diffLevel)
                ))
                state.routingTable[diffLevel] = diffLevelRoutes
            }
        }

        for (i in addrSize - 1 downTo diffLevel + 1) {
            val neighborEntry = neighbor.routingTable[i]
            if (neighborEntry == null || neighborEntry.routeLog.getBit(diffLevel) == 1) continue

            val levelRoutes = state.routingTable[i] ?: mutableListOf()
            if (neighborEntry.minId < minId(i)) {
                levelRoutes.clear()
                state.routingTable.remove(i)
            }

            if (neighborEntry.minId <= minId(i)) {
                var routeLog = neighborEntry.routeLog.setBit(diffLevel)
                for (j in diffLevel - 1 downTo 0) {
                    routeLog = routeLog.clearBit(j)
                }
                levelRoutes.add(RoutingTableEntry(
                        neighbor.address,
                        neighbor.identifier,
                        neighborEntry.cost + 1,
                        neighborEntry.minId,
                        routeLog
                ))
                state.routingTable[i] = levelRoutes
            }
        }
    }

    /**
     * Select a new address based on the received neighbor updates.
     */
    private fun selectAddress() {
        // Select address 0 if no neighbors are currently known
        if (state.neighborUpdates.isEmpty()) {
            state.address = 0
            return
        }
        // Iterate over neighbors, that are sorted with the highest insertion point first in the list.
        val sortedNeighbors = state.neighborUpdates.values
                .sortedWith(compareByDescending<NeighborUpdate> { insertionPoint(it) }.thenBy { it.identifier })
                .toList()
        for (neighbor in sortedNeighbors) {
            val insertionPoint = insertionPoint(neighbor)
            for (i in insertionPoint downTo 0) {
                state.address = neighbor.address.toggleBit(i)
                updateRoutingTable()
                if (validateAddress()) {
                    return
                }
            }
        }
        state.address = -1
    }

    /**
     * The insertion point is the highest level for which no routing entry exists.
     */
    private fun insertionPoint(neighbor: NeighborUpdate): Int {
        for (i in addrSize - 1 downTo 0) {
            if (!neighbor.routingTable.containsKey(i)) {
                return i
            }
        }
        return 0
    }

    /**
     * Check if the address is (still) valid.
     */
    private fun validateAddress(): Boolean {
        if (state.address < 0) return false
        for (neighbor in state.neighborUpdates.values) {
            val diffLevel = levelSibling(neighbor.address, state.address)
            if (diffLevel < 0) {
                // the address is identical
                if (neighbor.identifier < state.identifier) {
                    return false
                }
            } else {
                val entry = neighbor.routingTable[diffLevel]
                if (entry != null && entry.minId < levelId(diffLevel - 1)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Determines the lowest identifier of a subgraph.
     */
    private fun levelId(level: Int): WalletAddress {
        var levelId = state.identifier
        for (i in level downTo 0) {
            if (minId(i) < levelId) {
                levelId = minId(i)
            }
        }
        return levelId
    }
    private fun levelId(nodeState: NeighborUpdate, level: Int): WalletAddress {
        var levelId = nodeState.identifier
        for (i in level downTo 0) {
            val entry = nodeState.routingTable[i]
            if (entry != null && entry.minId < levelId) {
                levelId = entry.minId
            }
        }
        return levelId
    }

    /**
     * Determine the highest order bit that differs.
     * Returns k ∈ [0, addrSize - 1] for a level-k sibling; -1 if the addresses are equal
     */
    private fun levelSibling(address1: Int, address2: Int): Int {
        for (i in addrSize - 1 downTo 0) {
            if (address1.getBit(i) != address2.getBit(i)) {
                return i
            }
        }
        return -1
    }

    /**
     * Determines the lowest identifier of the routes on a specific level
     */
    private fun minId(level: Int): WalletAddress {
        var minId = "INFINITE"
        val routes = state.routingTable[level]
        if (routes != null) {
            for (route in routes) {
                if (route.minId < minId) minId = route.minId
            }
        }
        return minId
    }

    class NodeState (
            /**
             * Transient routing address
             * The routing address reflects the position of the node in the network.
             */
            var address: Int,
            /**
             * Static identifier of a node
             * The node's wallet address is used as its identifier that remains constant the whole simulation.
             */
            val identifier: WalletAddress,
            /**
             * Current routing table.
             * The keys are the levels in the address tree.
             */
            val routingTable: MutableMap<Int, MutableList<RoutingTableEntry>>,
            /**
             * Received neighbor updates since the last routing table refresh.
             * The keys are the neighbor's identifiers.
             */
            val neighborUpdates: MutableMap<WalletAddress, NeighborUpdate>,
            /**
             * Cycle number of the last update to this entry.
             */
            var lastUpdate: Int
    )

    class RoutingTableEntry (
            /**
             * Address to forward packets for this subtree.
             */
            var nextHop: Int,
            /**
             * Identifier of the next hop.
             */
            var nextHopIdentifier: WalletAddress,
            /**
             * The cost of the route.
             */
            var cost: Int,
            /**
             * Minimum identifier in the address subtree
             * This property is used to validate the current routing address.
             */
            var minId: WalletAddress,
            /**
             * Log of the route's travelling in the address tree.
             * This property is used for loop avoidance.
             */
            var routeLog: Int
    )

    data class NeighborUpdate (
            /**
             * Current address of the neighbor
             */
            val address: Int,
            /**
             * Static identifier of the neighbor
             */
            val identifier: WalletAddress,
            /**
             * Current routing table of the neighbor
             */
            val routingTable: Map<Int, NeighborUpdateEntry>,
            /**
             * Cycle number of the last update.
             */
            val lastUpdate: Int
    )

    data class NeighborUpdateEntry (
            /**
             * The cost of the route.
             */
            val cost: Int,
            /**
             * Minimum identifier in the address subtree
             * This property is used to validate the current routing address.
             */
            var minId: WalletAddress,
            /**
             * Log of the route's travelling in the address tree.
             * This property is used for loop avoidance.
             */
            val routeLog: Int
    )

    data class LookupTableEntry (
            /**
             * Address of the node that stored this entry
             */
            val address: Int,
            /**
             * Stored information about the node
             */
            val peer: Peer,
            /**
             * Last update on this entry (cycle number)
             */
            val lastUpdate: Int
    )

    /**
     * Read and write the bit of an Integer on position i.
     */
    private fun Int.getBit(i: Int): Int = (this shr i) and 1
    private fun Int.setBit(i: Int): Int = this or (1 shl i)
    private fun Int.clearBit(i: Int): Int = this and (1 shl i).inv()
    private fun Int.toggleBit(i: Int): Int = this xor (1 shl i)

    private fun Int.toAddressString(): String = if (this >= 0) this.toString(2).padStart(addrSize, '0') else this.toString()

    override fun toString(): String {
        val address = state.address.toAddressString()
        val header = "${self.name}: ${self.walletAddress}/${self.ipAddress}/$address (${state.neighborUpdates.size})\n"
        var routingTable = ""
        if (state.address >= 0) {
            for (i in 0 until addrSize) {
                val routes = state.routingTable[i]
                val siblingId = state.address.toggleBit(i).toAddressString().replaceRange(addrSize - i, addrSize, "X".repeat(i))
                if (routes == null) {
                     routingTable += "   $i $siblingId -\n"
                } else {
                    for (route in routes) {
                        val nextHop = route.nextHop.toAddressString()
                        val routeLog = route.routeLog.toAddressString()
                        val cost = route.cost.toString().padStart(2, ' ')
                        routingTable += "   $i $siblingId $nextHop ${route.nextHopIdentifier} $cost ${route.minId} $routeLog\n"
                    }
                }
            }
        }
        return header + routingTable
    }
}
