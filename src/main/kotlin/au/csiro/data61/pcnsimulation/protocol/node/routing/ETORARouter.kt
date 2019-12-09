package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetworkException
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class ETORARouter(
        self: Peer,
        override val socket: IPSocket,
        override val strategy: Strategy,
        initialKnownPeers: Map<WalletAddress, Peer>,
        initialKnownChannels: Set<StaticChannelInformation>,
        private val ttl: Int,
        cacheSize: Int
) : AbstractRouter(self, initialKnownPeers, initialKnownChannels, cacheSize) {
    companion object {
        val log by logger()
    }

    private val destinations = mutableMapOf<WalletAddress, Destination>()

    init {
        socket.registerReceiveCallback { packet ->
            when (val message = packet.payload) {
                is QRYPacket -> {
                    val senderId = neighbors.values.single { it.peer.ipAddress == packet.sender }.peer.walletAddress
                    onQRYPacket(message.destinationId, message.ttl, senderId)
                }
                is UPDPacket -> onUPDPacket(message.destinationId, message.senderHeight, message.routes, message.ttl)
                is CLRPacket -> onCLRPacket(message.destinationId, message.rrefLevel, message.ttl)
                is FindRoutesRequest -> {
                    val lastNeighbor = neighbors.values.single { it.peer.ipAddress == packet.sender }
                    val response = findRoutes(message.target, message.ttl, lastNeighbor)
                    socket.respond(packet.sender, packet.id, response)
                }
            }
        }
    }

    override suspend fun onChannelAddedOrUpdated(channel: StaticChannelInformation, peer: Peer) {
        if (destinations.any() && !destinations.values.first().links.any { it.id == peer.walletAddress }) {
            onNewLink(peer.walletAddress)
        }
    }

    override suspend fun onChannelRemoved(peer: WalletAddress) {
        onLinkDown(peer)
    }

    override suspend fun findRoutes(target: WalletAddress): Set<Route> {
        // Determine routes
        val routesResponse = findRoutes(target, ttl, null)
        if (routesResponse.failed) {
            return setOf()
        }
        return routesResponse.routes
    }

    private suspend fun findRoutes(target: WalletAddress, ttl: Int, lastNeighbor: Neighbor?): FindRoutesResponse {
        // If the destination is reached, respond with route
        if (target == self.walletAddress && lastNeighbor != null) {
            val channelInfo = strategy.information.disclose(lastNeighbor.channel.toDynamicChannelInformation(lastNeighbor.peer.walletAddress, ChannelFee.ZERO))
            val routes = setOf(Route(listOf(channelInfo), mapOf(Pair(self.walletAddress, self))))
            return FindRoutesResponse(routes)
        }

        // If ttl is zero or lower, respond  with failure
        if (ttl <= 0) {
            log.warn("TTL used up for FindRoutesRequest to $target at ${self.walletAddress}")
            return FindRoutesResponse(setOf(), true)
        }

        // Find the next hop
        val link = getDownstreamLink(target, ttl)
        val response = if (link == null) {
            // No route found
            FindRoutesResponse(setOf(), true)
        } else {
            // Next hop found
            val nextHop = neighbors.getValue(link.id)
            try {
                val resp = socket.request(nextHop.peer.ipAddress, FindRoutesRequest(target, ttl - 1), 500) as FindRoutesResponse
                if (lastNeighbor != null) {
                    val newRoutes = resp.routes.map {
                        val fee = strategy.fee.determine(nextHop.peer.walletAddress)
                        val channelInfo = strategy.information.disclose(lastNeighbor.channel.toDynamicChannelInformation(lastNeighbor.peer.walletAddress, fee))
                        it.copy(
                                channels = listOf(channelInfo) + it.channels,
                                peers = it.peers.plus(Pair(self.walletAddress, self))
                        )
                    }.toSet()
                    resp.copy(routes = newRoutes)
                } else {
                    resp
                }
            } catch (e: IPNetworkException) {
                log.warn("Sending FindRoutesRequest from ${self.walletAddress} to ${nextHop.peer.walletAddress} failed.", e)
                FindRoutesResponse(setOf(), true)
            }
        }
        response.routes.flatMap { it.peers.values }.forEach { addToPeerCache(it) }
        return response
    }

    data class FindRoutesRequest(
            val target: WalletAddress,
            val ttl: Int
    )

    data class FindRoutesResponse(
            val routes: Set<Route>,
            val failed: Boolean = false
    )

    private val awaitRoute = mutableMapOf<WalletAddress, Channel<Boolean>>()

    /**
     * Find best suited downstream link.
     *
     * The routes returned by E-TORA are not always up-to-date.
     * Hence, we use them only for deciding on the best downstream link, instead of returning them immediately.
     */
    private suspend fun getDownstreamLink(did: WalletAddress, remainingTtl: Int): NeighborLink? {
        val dest = destinations.getOrPut(did) { createDestination(did) }
        if (dest.links.all { it.status == LinkStatus.UNDIRECTED } && !dest.routeRequired) {
            // No directed links and an unset route required flag -> broadcast a QRY packet
            dest.routeRequired = true
            dest.remainingTtl = remainingTtl
            for (link in dest.links) {
                try {
                    socket.notify(neighbors.getValue(link.id).peer.ipAddress, QRYPacket(did, remainingTtl - 1))
                } catch (e: IPNetworkException) {
                    log.warn("QRY packet not delivered.", e)
                }
            }
            // Wait for UPD packet to arrive
            val channel = awaitRoute.getOrPut(dest.id) { Channel(1) }
            withTimeoutOrNull(500) { channel.receive() }
        }
        // Only proceed if at least one downstream link exists
        if (!dest.links.any { it.status == LinkStatus.DOWNSTREAM }) {
            log.warn("No downstream link found to destination $did at node ${self.walletAddress}.")
            return null
        }

        val validRoutes = dest.routes.filter { route ->
            // Filter routes for those using downstream links
            val nextHopWallet = route.channels.first().toWallet
            val nextHopLink = dest.links.single { it.id == nextHopWallet }
            nextHopLink.status == LinkStatus.DOWNSTREAM
        }.toList()
        val shortestRouteLength = validRoutes.map { it.channels.size }.min() ?: 0
        val cRoutes = validRoutes.map { route ->
            /**
             * E-TORA magic (Equation 1)
             * Calculate the value of c that should be maximized for the selected route.
             * Energy is replaced by liquidity in this implementation.
             */
            val minLiquidity = route.channels.map { it.liquidity }.min()!!
            val avgLiquidity = route.channels.map { it.liquidity }.average()
            val hopCount = route.channels.size
            val alpha = 0.7 // Weight the two metrics liquidity and hop-count
            val c = alpha * (minLiquidity / avgLiquidity) + (1.0 - alpha) * (shortestRouteLength.toDouble() / hopCount.toDouble())
            Pair(c, route)
        }.sortedByDescending { it.first }.toList()

        return if (cRoutes.isEmpty()) {
            dest.links.filter { it.status == LinkStatus.DOWNSTREAM }.minBy { it.height }!!
        } else {
            val nextHopWallet = cRoutes.first().second.channels.first().toWallet
            dest.links.single { it.id == nextHopWallet }
        }
    }

    private suspend fun onQRYPacket(did: WalletAddress, ttl: Int, senderId: WalletAddress) {
        if (ttl <= 0) return

        val dest = destinations.getOrPut(did) { createDestination(did) }
        if (dest.links.all { it.status == LinkStatus.UNDIRECTED } && !dest.routeRequired) {
            // a: No directed links and an unset route required flag -> broadcast a QRY packet
            dest.routeRequired = true
            dest.remainingTtl = ttl
            for (link in dest.links) {
                try {
                    socket.notify(neighbors.getValue(link.id).peer.ipAddress, QRYPacket(did, ttl - 1))
                } catch (e: IPNetworkException) {
                    log.warn("QRY packet not delivered.", e)
                }
            }
        } else if (dest.links.all { it.status == LinkStatus.UNDIRECTED } && dest.routeRequired) {
            // b: No directed links but route required flag is set -> discard
            return
        } else if (dest.links.any { it.status == LinkStatus.DOWNSTREAM } && dest.height.isNull()) {
            // c: At least one downstream link and a null height -> adjust own height and broadcast UPD packet
            val minLink = dest.links.filter { it.status == LinkStatus.DOWNSTREAM }.minBy { it.height }!!
            dest.height = dest.height.toReferenceLevel(minLink.height)
            dest.routeRequired = false
            // Update link state and broadcast an UPD with the new height
            for (link in dest.links) {
                link.updateStatus(dest.height)
                sendNeighborUPD(link.id, dest, ttl - 1)
            }
            dest.lastUPDSent = currentCycle
        } else if (dest.links.any { it.status == LinkStatus.DOWNSTREAM } && !dest.height.isNull()) {
            // d: At least one downstream link and a non-null height -> compare time and broadcast UPD or discard
            val senderLink = dest.links.single { it.id == senderId }
            if (dest.lastUPDSent < senderLink.activeSince) {
                for (link in dest.links) {
                    sendNeighborUPD(link.id, dest, ttl - 1)
                }
                dest.lastUPDSent = currentCycle
            } else {
                return
            }
        }
    }

    private val deferredNotifications = mutableListOf<Pair<IPAddress, Any>>()

    override suspend fun onCycle() {
        // Send QRY packets for new links deferred to avoid race condition in synchronous networks
        for ((addr, packet) in deferredNotifications) {
            if (neighbors.values.any { it.peer.ipAddress == addr }) {
                try {
                    socket.notify(addr, packet)
                } catch (e: IPNetworkException) {
                    log.warn("Deferred packet of type ${packet.javaClass.simpleName} not delivered to $addr.", e)
                }
            }
        }
    }

    private fun onNewLink(partner: WalletAddress) {
        for (dest in destinations.values) {
            // Add new link to destination
            val neighbor = neighbors.getValue(partner)
            if (dest.id == partner) {
                // Add one hop route
                val channelInfo = strategy.information.disclose(neighbor.channel.toDynamicChannelInformation(self.walletAddress, ChannelFee.ZERO))
                dest.routes.add(Route(
                        listOf(channelInfo),
                        mapOf(Pair(partner, neighbor.peer))
                ))
                dest.links.add(NeighborLink(partner, currentCycle, NodeHeight(id = partner).toZero()).updateStatus(dest.height))
            } else {
                dest.links.add(NeighborLink(partner, currentCycle))
            }
            // Query the new link if routeRequired is set
            if (dest.routeRequired) {
                deferredNotifications.add(Pair(neighbor.peer.ipAddress, QRYPacket(dest.id, dest.remainingTtl)))
            }
        }
    }

    private fun onLinkDown(partner: WalletAddress) {
        // Remove link from all destinations
        for (dest in destinations.values) {
            dest.links.removeAll { it.id == partner }
            dest.routes.removeAll {
                val nextHopWallet = it.channels.first().toWallet
                nextHopWallet == partner
            }
            // Maintain routes: Case 1 (Generate)
            if (!dest.links.any { it.status == LinkStatus.DOWNSTREAM }) {
                if (dest.links.any { it.status == LinkStatus.UPSTREAM }) {
                    dest.height = NodeHeight(self.walletAddress, currentCycle, self.walletAddress, false, 0)
                } else {
                    dest.height = dest.height.toNull()
                }
                // Update link state and broadcast an UPD with the new height
                for (link in dest.links) {
                    link.updateStatus(dest.height)
                    deferredNotifications.add(createNeighborUPD(link.id, dest, ttl - dest.height.delta))
                }
                dest.lastUPDSent = currentCycle
            }
        }
    }

    private suspend fun onUPDPacket(did: WalletAddress, senderHeight: NodeHeight, routes: Set<Route>, ttl: Int) {
        if (ttl <= 0) return

        val dest = destinations.getOrPut(did) { createDestination(did) }
        // Update height of neighbor
        val neighborLink = dest.links.single { it.id == senderHeight.id }
        neighborLink.height = senderHeight
        // Removing old routes of the neighbor (indicated by the next hop equals the neighbor)
        dest.routes.removeAll {
            val nextHopWallet = it.channels.first().toWallet
            nextHopWallet == neighborLink.id
        }
        // Add personalized routes from the neighbor
        dest.routes.addAll(routes)
        routes.flatMap { it.peers.values }.forEach { addToPeerCache(it) }

        if (dest.routeRequired) {
            // a: Set new height with received reference level
            dest.height = dest.height.toReferenceLevel(senderHeight)
            dest.routeRequired = false
            // Update all link statuses and broadcast an UPD with the new height
            for (link in dest.links) {
                link.updateStatus(dest.height)
                sendNeighborUPD(link.id, dest, ttl - 1)
            }
            dest.lastUPDSent = currentCycle
            if (dest.links.any { it.status == LinkStatus.DOWNSTREAM } && awaitRoute.containsKey(did)) {
                val ch = awaitRoute.getValue(did)
                if (ch.isEmpty) ch.send(true)
            }
        } else {
            // b: Update status of this link
            neighborLink.updateStatus(dest.height)
            // Maintain routes: Cases 2-5
            if (!dest.links.any { it.status == LinkStatus.DOWNSTREAM }) {
                val refLevels = dest.links
                        .map { it.height }
                        .sorted()
                        .distinctBy { Triple(it.tau, it.oid, it.reflected) }
                val maxRefLevel = refLevels.last()
                if (refLevels.size > 1) {
                    // Case 2 (Propagate)
                    val minDelta = dest.links
                            .map { it.height }
                            .sorted()
                            .first { it.tau == maxRefLevel.tau && it.oid == maxRefLevel.oid && it.reflected == maxRefLevel.reflected }
                            .delta
                    dest.height = dest.height.copy(
                            tau = maxRefLevel.tau,
                            oid = maxRefLevel.oid,
                            reflected = maxRefLevel.reflected,
                            delta = minDelta - 1
                    )
                } else if (refLevels.size == 1 && !maxRefLevel.reflected) {
                    // Case 3 (Reflect)
                    dest.height = dest.height.copy(
                            tau = maxRefLevel.tau,
                            oid = maxRefLevel.oid,
                            reflected = true,
                            delta = 0
                    )
                } else if (refLevels.size == 1 && maxRefLevel.reflected && maxRefLevel.oid == self.walletAddress) {
                    // Case 4 (Detect)
                    dest.height = dest.height.copy(reflected = true)
                    onCLRPacket(dest.id, dest.height, ttl - 1)
                    return // don't update link state and broadcast UPD
                } else if (refLevels.size == 1 && maxRefLevel.reflected && maxRefLevel.oid != self.walletAddress) {
                    // Case 5 (Generate)
                    dest.height = dest.height.copy(
                            tau = currentCycle,
                            oid = self.walletAddress,
                            reflected = false,
                            delta = 0
                    )
                }
                // Update link state and broadcast an UPD with the new height
                for (link in dest.links) {
                    link.updateStatus(dest.height)
                    sendNeighborUPD(link.id, dest, ttl - 1)
                }
                dest.lastUPDSent = currentCycle
            }
        }
    }

    private suspend fun onCLRPacket(did: WalletAddress, rRefLevel: NodeHeight, ttl: Int) {
        if (ttl <= 0) return

        val dest = destinations.getOrPut(did) { createDestination(did) }
        if (dest.height.tau == rRefLevel.tau && dest.height.oid == rRefLevel.oid && dest.height.reflected == rRefLevel.reflected) {
            // a: ref level matches -> clear routes
            dest.height = dest.height.toNull()
            for (link in dest.links) {
                if (link.id == did) {
                    link.height = link.height.toZero()
                } else {
                    link.height = link.height.toNull()
                }
                link.updateStatus(dest.height)
                try {
                    socket.notify(neighbors.getValue(link.id).peer.ipAddress, CLRPacket(did, rRefLevel, ttl - 1))
                } catch (e: IPNetworkException) {
                    log.warn("CLR packet not delivered.", e)
                }
            }
        } else {
            // b: ref level doesn't match
            dest.links
                    .filter { it.height.tau == rRefLevel.tau && it.height.oid == rRefLevel.oid == it.height.reflected == rRefLevel.reflected }
                    .forEach { link ->
                        if (link.id == did) {
                            link.height = link.height.toZero()
                        } else {
                            link.height = link.height.toNull()
                        }
                        link.updateStatus(dest.height)
                    }
            // Maintain routes: Case 1 (Generate)
            if (!dest.links.any { it.status == LinkStatus.DOWNSTREAM }) {
                if (dest.links.any { it.status == LinkStatus.UPSTREAM }) {
                    dest.height = NodeHeight(self.walletAddress, currentCycle, self.walletAddress, false, 0)
                } else {
                    dest.height = dest.height.toNull()
                }
                // Update link state and broadcast an UPD with the new height
                for (link in dest.links) {
                    link.updateStatus(dest.height)
                    sendNeighborUPD(link.id, dest, ttl - 1)
                }
                dest.lastUPDSent = currentCycle
            }
        }
    }

    private suspend fun sendNeighborUPD(nid: WalletAddress, dest: Destination, ttl: Int) {
        val (addr, packet) = createNeighborUPD(nid, dest, ttl)
        try {
            socket.notify(addr, packet)
        } catch (e: IPNetworkException) {
            log.warn("UPD packet from ${self.walletAddress} to $nid not delivered.", e)
        }
    }

    private fun createNeighborUPD(nid: WalletAddress, dest: Destination, ttl: Int): Pair<IPAddress, UPDPacket> {
        val neighbor = neighbors.getValue(nid)
        val neighborRoutes = dest.routes.filter { route ->
            // Filter routes for those using downstream links and not containing the neighbor already
            val nextHopWallet = route.channels.first().toWallet
            val nextHopLink = dest.links.single { it.id == nextHopWallet }
            nextHopLink.status == LinkStatus.DOWNSTREAM && !route.peers.containsKey(nid)
        }.map { route ->
            // Append channel from neighbor to routes (incl. fees)
            val nextHopWallet = route.channels.first().toWallet
            val fee = strategy.fee.determine(nextHopWallet)
            val channelInfo = strategy.information.disclose(neighbor.channel.toDynamicChannelInformation(nid, fee))
            route.copy(
                    channels = listOf(channelInfo) + route.channels,
                    peers = route.peers.plus(Pair(self.walletAddress, self))
            )
        }.toSet()
        return Pair(neighbor.peer.ipAddress, UPDPacket(dest.id, dest.height, neighborRoutes, ttl - 1))
    }

    private fun createDestination(did: WalletAddress): Destination {
        val dest = Destination(did, NodeHeight(self.walletAddress), false, ttl, Int.MIN_VALUE, mutableListOf(), mutableListOf())

        for (neighbor in neighbors.values) {
            val nid = neighbor.peer.walletAddress
            val link = NeighborLink(nid, currentCycle)
            if (did == nid) {
                link.height = link.height.toZero()
                link.updateStatus(dest.height)
                val channelInfo = strategy.information.disclose(neighbor.channel.toDynamicChannelInformation(self.walletAddress, ChannelFee.ZERO))
                dest.routes.add(Route(
                        listOf(channelInfo),
                        mapOf(Pair(nid, neighbor.peer))
                ))
            }
            dest.links.add(link)
        }

        return dest
    }

    data class QRYPacket(
            val destinationId: WalletAddress,
            val ttl: Int
    )
    data class UPDPacket(
            val destinationId: WalletAddress,
            val senderHeight: NodeHeight,
            val routes: Set<Route>,
            val ttl: Int
    )
    data class CLRPacket(
            val destinationId: WalletAddress,
            val rrefLevel: NodeHeight,
            val ttl: Int
    )

    class Destination(
            val id: WalletAddress,
            var height: NodeHeight,
            var routeRequired: Boolean,
            var remainingTtl: Int,
            var lastUPDSent: Int,
            val links: MutableList<NeighborLink>,
            val routes: MutableList<Route>
    )

    class NeighborLink(
            /**
             * Wallet id of the neighbor
             */
            val id: WalletAddress,
            /**
             * The time the link became active
             */
            val activeSince: Int,
            /**
             * The height of this neighbor
             */
            var height: NodeHeight = NodeHeight(id = id),
            /**
             * Current status of this link
             */
            var status: LinkStatus = LinkStatus.UNDIRECTED
    ) {
        fun updateStatus(h: NodeHeight): NeighborLink {
            if (height.isNull()) {
                status = LinkStatus.UNDIRECTED
            } else if (h.isNull() || height < h) {
                status = LinkStatus.DOWNSTREAM
            } else if (height > h) {
                status = LinkStatus.UPSTREAM
            }
            return this
        }
    }

    enum class LinkStatus {
        UNDIRECTED, UPSTREAM, DOWNSTREAM
    }

    data class NodeHeight(
            /**
             * Id of the node
             */
            val id: WalletAddress,
            /**
             * Time the reference level was created
             */
            val tau: Int = -1,
            /**
             * Id of the node that created the reference level
             */
            val oid: WalletAddress = "",
            /**
             * Distinguishes between original and reflected reference level
             */
            val reflected: Boolean = false,
            /**
             * Order nodes in respect to the reference level
             */
            val delta: Int = -1
    ): Comparable<NodeHeight> {
        fun toZero() = this.copy(tau = 0, oid = "", reflected = false, delta = 0)
        fun toNull() = this.copy(tau = -1, oid = "", reflected = false, delta = -1)

        fun toReferenceLevel(h: NodeHeight): NodeHeight = this.copy(tau = h.tau, oid = h.oid, reflected = h.reflected, delta = h.delta + 1)

        fun isZero() = this == this.toZero()
        fun isNull() = this == this.toNull()

        override operator fun compareTo(other: NodeHeight): Int {
            if(tau < other.tau) return -1
            if(tau > other.tau) return 1
            if(oid < other.oid) return -1
            if(oid > other.oid) return 1
            if(!reflected && other.reflected) return -1
            if(reflected && !other.reflected) return 1
            if(delta < other.delta) return -1
            if(delta > other.delta) return 1
            if(id < other.id) return -1
            if(id > other.id) return 1
            return 0 // equal
        }
    }
}
