package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy

interface Router {
    /**
     * The socket for accessing the communication network.
     */
    val socket: IPSocket

    /**
     * Strategies to inject behaviour in the router.
     */
    val strategy: Strategy

    /**
     * All peers that are currently known to the router.
     */
    val knownPeers: Map<WalletAddress, Peer>

    /**
     * Static information about all channels that are known to the router.
     */
    val knownChannels: Set<StaticChannelInformation>


    /**
     * Resolve ip-address and get all information about the peer.
     */
    suspend fun findPeer(target: WalletAddress): Peer

    /**
     * Find a predefined route to a target peer.
     */
    suspend fun findRoute(targets: List<WalletAddress>): Route

    /**
     * Find some possible routes to a target peer (routes cannot be assumed to be best and complete).
     */
    suspend fun findRoutes(target: WalletAddress): Set<Route>

    /**
     * Set the current [cycleNumber] and perform cycle-dependent tasks.
     */
    suspend fun cycle(cycleNumber: Int)

    /**
     * Add or update a local channel.
     */
    suspend fun addOrUpdateChannel(channel: StaticChannelInformation, peer: Peer)

    /**
     * Remove a local channel to a specific [peer].
     */
    suspend fun removeChannel(peer: WalletAddress)

    /**
     * Report an interaction (not routing related)
     */
    fun report(topic: String, target: WalletAddress, successful: Boolean)
}
