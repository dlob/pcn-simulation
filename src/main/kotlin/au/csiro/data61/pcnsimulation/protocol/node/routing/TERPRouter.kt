package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetworkException
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import kotlin.math.min

class TERPRouter (
        self: Peer,
        override val socket: IPSocket,
        override val strategy: Strategy,
        initialKnownPeers: Map<WalletAddress, Peer>,
        initialKnownChannels: Set<StaticChannelInformation>,
        private val radius: Int,
        private val routeLifetime: Int,
        cacheSize: Int
) : AbstractRouter(self, initialKnownPeers, initialKnownChannels, cacheSize) {

    companion object {
        val log by logger()
    }

    init {
        socket.registerReceiveCallback { packet ->
            when (val message = packet.payload) {
                is RouteRequest -> {
                    val lastHop = neighbors.values.single { it.peer.ipAddress == packet.sender }
                    val response = findRoutes(message, lastHop)
                    socket.respond(packet.sender, packet.id, response)
                }
                is TrustRequest -> {
                    socket.respond(packet.sender, packet.id, TrustResponse(directTrust(message.target)))
                }
            }
        }
    }

    private var lastSequenceNumber: Int = 0
    private var lastBroadcastID: Int = 0
    private var routingTable = mutableMapOf<WalletAddress, RoutingTableEntry>()
    private var routingRequestReceived = mutableMapOf<Pair<WalletAddress, Int>, Int>()

    override suspend fun onChannelRemoved(peer: WalletAddress) {
        for (r in routingTable.values.toList()) {
            if (r.nextHopWallet == peer) routingTable.remove(r.destinationWallet)
        }
    }

    override suspend fun findRoutes(target: WalletAddress): Set<Route> {
        val routesResponse = findRoutes(RouteRequest(
                ++lastBroadcastID,
                self.walletAddress,
                ++lastSequenceNumber,
                target,
                0,
                0,
                radius + 1,
                ChannelFee.ZERO,
                Double.MAX_VALUE
        ), null)
        if (routesResponse.failed) {
            return setOf()
        }
        return setOf(routesResponse.route)
    }

    private suspend fun findRoutes(request: RouteRequest, lastHop: Neighbor?): RouteResponse {
        if (lastHop != null) {
            // If channel has no remaining liquidity -> failed
            if (lastHop.neighborLiquidity() <= 0.0) {
                log.warn("Channel from ${lastHop.peer.walletAddress} to ${self.walletAddress} has not enough liquidity.")
                return RouteResponse(request, self, true)
            }
            // If RREQ has already been received -> failed
            if (routingRequestReceived.containsKey(Pair(request.sourceWallet, request.broadcastID))) {
                log.warn("RREQ from (${request.sourceWallet}, ${request.broadcastID}) has already been received.")
                return RouteResponse(request, self, true)
            }
            routingRequestReceived[Pair(request.sourceWallet, request.broadcastID)] = currentCycle

            // Update reverse path to source
            addToRoutingTable(request.sourceWallet, request.sourceSequenceNumber, lastHop.peer.walletAddress, request.hopCount, request.fee, request.maxFlow)

            // If destination is reached -> Reply with RREP
            if (request.destinationWallet == self.walletAddress) {
                val lastHopChannelInfo = strategy.information.disclose(lastHop.channel.toDynamicChannelInformation(lastHop.peer.walletAddress, ChannelFee.ZERO))
                return RouteResponse(
                        request.sourceWallet,
                        request.sourceSequenceNumber,
                        self.walletAddress,
                        ++lastSequenceNumber,
                        1,
                        lastHopChannelInfo.fee,
                        lastHopChannelInfo.liquidity,
                        Route(listOf(lastHopChannelInfo), mapOf(Pair(self.walletAddress, self)))
                )
            }

            // If TTL is exhausted -> ignore
            if (request.ttl <= 0) {
                return RouteResponse(request, self, true)
            }
        }

        // If route to destination is known and up-to-date -> Forward RREP to next hop
        val entry = routingTable[request.destinationWallet]
        var entryRrep: RouteResponse? = null
        if (entry != null && entry.destinationSequenceNumber >= request.destinationSequenceNumber) {
            val nextHop = neighbors.getValue(entry.nextHopWallet)
            entryRrep = sendRREQ(request, entry.destinationSequenceNumber, lastHop, nextHop)
            // only rely on the routing table if the next hop is a trusted node or the destination
            if (trust(nextHop.peer.walletAddress, entryRrep.route.afterNextHop()) == 1 || request.destinationWallet == nextHop.peer.walletAddress) {
                return entryRrep
            }
        }

        // Up-to-date route is unknown or trust too low: Broadcast RREP to neighbors
        var bestRrep = RouteResponse(request, self, true)
        var bestRrepCrf = Double.MAX_VALUE
        for (nextHop in neighbors.values) {
            val nextHopWallet = nextHop.peer.walletAddress
            if (lastHop != null && nextHopWallet == lastHop.peer.walletAddress) continue
            // Prevent to send RREQ again to next hop node of routing table
            val rrep = if (entryRrep != null && nextHopWallet == entry!!.nextHopWallet) {
                entryRrep
            } else {
                sendRREQ(request, request.destinationSequenceNumber, lastHop, nextHop)
            }
            if (rrep.failed) continue
            val rrepCrf = crf(nextHopWallet, rrep.route.afterNextHop(), rrep.maxFlow, rrep.fee, rrep.hopCount)
            if (rrepCrf < bestRrepCrf) {
                bestRrep = rrep
                bestRrepCrf = rrepCrf
            }
        }
        return bestRrep
    }

    private suspend fun sendRREQ(prreq: RouteRequest, destinationSequenceNumber: Int, lastHop: Neighbor?, nextHop: Neighbor): RouteResponse {
        val nextHopWallet = nextHop.peer.walletAddress
        val nextHopFee = if (lastHop == null) ChannelFee.ZERO else strategy.fee.determine(lastHop.peer.walletAddress)
        val nextHopChannelInfo = strategy.information.disclose(nextHop.channel.toDynamicChannelInformation(nextHopWallet, nextHopFee))
        val rreq = prreq.copy(
                destinationSequenceNumber = destinationSequenceNumber,
                hopCount = prreq.hopCount + 1,
                ttl = prreq.ttl - 1,
                fee = prreq.fee.add(nextHopChannelInfo.fee),
                maxFlow = min(prreq.maxFlow, nextHopChannelInfo.liquidity)
        )
        return try {
            val rrep = socket.request(nextHop.peer.ipAddress, rreq, 500) as RouteResponse
            if (!rrep.failed) {
                addToRoutingTable(rrep.destinationWallet, rrep.destinationSequenceNumber, nextHopWallet, rrep.hopCount, rrep.fee, rrep.maxFlow)
            }
            rrep.route.peers.values.forEach { addToPeerCache(it) }
            if (lastHop == null) {
                rrep
            } else {
                val lastHopFee = strategy.fee.determine(nextHopWallet)
                val lastHopChannelInfo = strategy.information.disclose(lastHop.channel.toDynamicChannelInformation(lastHop.peer.walletAddress, lastHopFee))
                rrep.copy(
                        hopCount = rrep.hopCount + 1,
                        fee = rrep.fee.add(lastHopChannelInfo.fee),
                        maxFlow = min(rrep.maxFlow, lastHopChannelInfo.liquidity),
                        route = Route(
                                channels = listOf(lastHopChannelInfo) + rrep.route.channels,
                                peers = rrep.route.peers.plus(Pair(self.walletAddress, self)))
                )
            }
        } catch (e: IPNetworkException) {
            log.warn("Sending RREQ from ${self.walletAddress} to $nextHopWallet failed.", e)
            RouteResponse(rreq, self, true)
        }
    }

    data class RoutingTableEntry (
            val destinationWallet: WalletAddress,
            val destinationSequenceNumber: Int,
            val nextHopWallet: WalletAddress,
            val hopCount: Int,
            val expirationCycle: Int,
            val fee: ChannelFee,
            val maxFlow: Double
    )

    data class RouteRequest (
            val broadcastID: Int,
            val sourceWallet: WalletAddress,
            val sourceSequenceNumber: Int,
            val destinationWallet: WalletAddress,
            val destinationSequenceNumber: Int,
            val hopCount: Int,
            val ttl: Int,
            val fee: ChannelFee,
            val maxFlow: Double
    )

    data class RouteResponse(
            val sourceWallet: WalletAddress,
            val sourceSequenceNumber: Int,
            val destinationWallet: WalletAddress,
            val destinationSequenceNumber: Int,
            val hopCount: Int,
            val fee: ChannelFee,
            val maxFlow: Double,
            val route: Route,
            val failed: Boolean = false
    ) {
        constructor(rreq: RouteRequest, self: Peer, failed: Boolean = false) : this(
                rreq.sourceWallet,
                rreq.sourceSequenceNumber,
                rreq.destinationWallet,
                rreq.destinationSequenceNumber,
                0,
                ChannelFee.ZERO,
                0.0,
                Route(peers = mapOf(Pair(self.walletAddress, self))),
                failed
        )
    }

    private suspend fun addToRoutingTable(destinationWallet: WalletAddress, destinationSequenceNumber: Int, nextHopWallet: WalletAddress, hopCount: Int, fee: ChannelFee, maxFlow: Double) {
        // Check and add/update path to destination
        val entry = routingTable[destinationWallet]
        val newEntry = RoutingTableEntry(
                destinationWallet,
                destinationSequenceNumber,
                nextHopWallet,
                hopCount,
                currentCycle + routeLifetime,
                fee,
                maxFlow
        )
        if (entry == null || entry.destinationSequenceNumber < destinationSequenceNumber) {
            // Entry does not exist or is older -> add/replace
            routingTable[destinationWallet] = newEntry
        } else if (entry.destinationSequenceNumber == destinationSequenceNumber) {
            // Entry exists, but eventually better
            val entryCrf = crf(entry.nextHopWallet, null, entry.maxFlow, entry.fee, entry.hopCount)
            val newEntryCrf = crf(newEntry.nextHopWallet, null, newEntry.maxFlow, newEntry.fee, newEntry.hopCount)
            routingTable[destinationWallet] = if (newEntryCrf <= entryCrf) newEntry else entry
        }
    }

    override suspend fun onCycle() {
        routingTable = routingTable
                .filter { it.value.expirationCycle  > currentCycle }
                .toMap()
                .toMutableMap()
        routingRequestReceived.clear()
        if (routingTable.any { !neighbors.containsKey(it.value.nextHopWallet) }) {
            log.warn("RoutingTable is not in sync with the actual channels.")
        }
    }

    private fun Route.afterNextHop(): Peer? =
        if (this.channels.size > 1) {
            val afterNextHopWallet = this.channels[1].toWallet
            this.peers.getValue(afterNextHopWallet)
        } else {
            null
        }

    /**
     * Composite routing function
     * Returns the costs to use a route [smaller is better; can be negative]
     */
    private suspend fun crf(nextHopWallet: WalletAddress, afterNextHop: Peer?, maxFlow: Double, fee: ChannelFee, hopCount: Int): Double {
        val w1 = 0.25 // weight of trust
        val w2 = 0.25 // weight of liquidity
        val w3 = 0.07 // weight of fixed fee
        val w4 = 0.18 // weight of rate fee
        val w5 = 0.25 // weight of hop count [w1 + w2 + w3 + w4 + w5 = 1]
        val t = trust(nextHopWallet, afterNextHop)
        return t * w1 + maxFlow * -1.0 * w2 + fee.fixed * w3 + fee.rate * w4 + hopCount * w5
    }

    /**
     * Trust (d ... trust threshold)
     *
     * Level    Trust values    Class of node
     * --------------------------------------
     * 1        (d, 1]          Trusted
     * 2        (0.5, d]        Less trusted
     * 3        0.5             Indecisive
     * 4        (0, 0.5)        Misbehaving
     */
    private val trustThreshold = 0.6
    private val reports = mutableMapOf<WalletAddress, Pair<Int, Int>>()

    override fun report(topic: String, target: WalletAddress, successful: Boolean) {
        val oldReport = reports.getOrDefault(target, Pair(0, 0))
        reports[target] = oldReport.copy(
                first = oldReport.first + 1,
                second = oldReport.second + if (successful) 1 else 0
        )
    }

    /**
     * level of trust in the next hop of a route considering direct and indirect trust
     */
    private suspend fun trust(nextHopWallet: WalletAddress, afterNextHop: Peer?): Int {
        val w1 = 0.6 // weight of direct trust
        val w2 = 0.4 // weight of indirect trust [w1 + w2 = 1]
        val dt = directTrust(nextHopWallet)
        val it = if (afterNextHop == null) 0.5 else indirectTrust(nextHopWallet, afterNextHop)
        val t = w1 * dt + w2 * it
        return when {
            t > trustThreshold -> 1
            t > 0.5 -> 2
            t == 0.5 -> 3
            else -> 4
        }
    }

    /**
     * direct trust in a neighbor based on reports
     */
    private fun directTrust(neighbor: WalletAddress): Double {
        val report = reports[neighbor]
        return if (report == null || report.first == 0) {
            0.5
        } else {
            val cf = report.second.toDouble()
            val tr = report.first.toDouble()
            cf / tr
        }
    }

    /**
     * trust of the node after next in the specified route in a neighbor (next node)
     */
    private suspend fun indirectTrust(nextHopWallet: WalletAddress, afterNextHop: Peer): Double {
        return try {
            val resp = socket.request(afterNextHop.ipAddress, TrustRequest(nextHopWallet), 500) as TrustResponse
            resp.trust
        } catch (e: IPNetworkException) {
            0.5
        } * directTrust(afterNextHop.walletAddress)
    }

    data class TrustRequest(val target: WalletAddress)
    data class TrustResponse(val trust: Double )
}
