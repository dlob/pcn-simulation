package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetworkException
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.message.request.Request
import au.csiro.data61.pcnsimulation.protocol.message.response.Response
import au.csiro.data61.pcnsimulation.protocol.node.NodeException
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import kotlinx.coroutines.runBlocking
import org.jgrapht.Graph
import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DefaultDirectedWeightedGraph
import org.jgrapht.graph.DefaultWeightedEdge

class BasicRouter (
        private val self: Peer,
        override val socket: IPSocket,
        override val strategy: Strategy,
        override val knownPeers: MutableMap<WalletAddress, Peer>,
        override val knownChannels: MutableSet<StaticChannelInformation>
) : Router {

    companion object {
        val log by logger()
    }

    private var currentCycle = 0

    override suspend fun findPeer(target: WalletAddress): Peer {
        return knownPeers[target]!!
    }

    override suspend fun findRoute(targets: List<WalletAddress>): Route {
        val graph = createGraph(targets.toSet() + setOf(self.walletAddress))
        val allPaths = AllDirectedPaths(graph).getAllPaths(self.walletAddress, targets.last(), true, targets.size)
        if (allPaths.isEmpty()) {
            throw NodeException("No path found from ${self.walletAddress} to ${targets.last()}.")
        }
        val path = allPaths.first { it.vertexList == listOf(self.walletAddress) + targets }
        val route = toRoute(graph, path)
        if (route == null)
            throw NodeException("No route found from ${self.walletAddress} to ${targets.last()}.")
        return route
    }

    override suspend fun findRoutes(target: WalletAddress): Set<Route> {
        val graph = createGraph(knownPeers.keys)
        val allPaths = AllDirectedPaths(graph).getAllPaths(self.walletAddress, target, true, 10)
        return allPaths
                .asSequence()
                .sortedBy { it.length }
                .mapNotNull { path -> runBlocking { toRoute(graph, path) } }
                .take(10)
                .toSet()
    }

    private suspend fun toRoute(graph: Graph<WalletAddress, DefaultWeightedEdge>, path: GraphPath<WalletAddress, DefaultWeightedEdge>) : Route? {
        try {
            val peers = path.vertexList.map { Pair(it, knownPeers[it]!!) }.toMap()
            val rawChannels = path.edgeList.map { Pair(graph.getEdgeSource(it), graph.getEdgeTarget(it)) }.toList()
            val finalChannelInfo = DynamicChannelInformation(rawChannels.last().first, rawChannels.last().second, graph.getEdgeWeight(path.edgeList.last()), ChannelFee.ZERO)
            val channels = rawChannels
                    .zipWithNext()
                    .map {
                        val toPeer = peers.getValue(it.first.second)
                        val request = BasicRouterChannelInfoRequest(it.first.first, it.first.second, it.second.second)
                        (socket.request(toPeer.ipAddress, request) as BasicRouterChannelInfoResponse).channelInfo
                    }
                    .plus(finalChannelInfo)
            return Route(channels, peers)
        } catch (e: IPNetworkException) {
            log.warn("Gathering route info failed.", e)
            // Ignore this route
            return null
        }
    }

    private fun createGraph(vertices: Set<WalletAddress>): Graph<WalletAddress, DefaultWeightedEdge> {
        val graph = DefaultDirectedWeightedGraph<WalletAddress, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        vertices.forEach { node -> graph.addVertex(node) }
        knownChannels.forEach {channel ->
            if (vertices.contains(channel.fromWallet) && vertices.contains(channel.toWallet)) {
                val from = graph.addEdge(channel.fromWallet, channel.toWallet)
                val to = graph.addEdge(channel.toWallet, channel.fromWallet)
                if (from != null) {
                    graph.setEdgeWeight(from, channel.fromLiquidity)
                }
                if (to != null) {
                    graph.setEdgeWeight(to, channel.toLiquidity)
                }
            }
        }
        return graph
    }

    override suspend fun cycle(cycleNumber: Int) {
        currentCycle = cycleNumber
    }

    override suspend fun addOrUpdateChannel(channel: StaticChannelInformation, peer: Peer) {
        knownChannels.removeIf { it.hasWallets(self.walletAddress, peer.walletAddress) }
        knownChannels.add(channel)
    }

    override suspend fun removeChannel(peer: WalletAddress) {
        knownChannels.removeIf { it.hasWallets(self.walletAddress, peer) }
    }

    init {
        socket.registerReceiveCallback { packet ->
            val message = packet.payload
            when(message) {
                is BasicRouterChannelInfoRequest -> {
                    if (message.toWallet == self.walletAddress) {
                        val channel = knownChannels.first { it.hasWallets(message.beforeWallet, message.toWallet) }
                        val dynChannel = channel.toDynamicChannelInformation(message.beforeWallet, strategy.fee.determine(message.afterWallet))
                        val disclosedDynChannel = strategy.information.disclose(dynChannel)
                        socket.respond(packet.sender, packet.id, BasicRouterChannelInfoResponse(disclosedDynChannel))
                    }
                }
            }
        }
    }

    data class BasicRouterChannelInfoRequest(
            val beforeWallet: WalletAddress,
            val toWallet: WalletAddress,
            val afterWallet: WalletAddress
    ) : Request()

    data class BasicRouterChannelInfoResponse(
            val channelInfo: DynamicChannelInformation
    ) : Response()

    override fun report(topic: String, target: WalletAddress, successful: Boolean) {}
}
