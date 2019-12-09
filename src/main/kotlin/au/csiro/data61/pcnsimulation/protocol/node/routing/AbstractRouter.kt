package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.node.NodeException

abstract class AbstractRouter (
        protected val self: Peer,
        initialKnownPeers: Map<WalletAddress, Peer>,
        initialKnownChannels: Set<StaticChannelInformation>,
        protected val cacheSize: Int
) : Router {

    companion object {
        val log by logger()
    }

    final override val knownPeers : Map<WalletAddress, Peer>
        get() = peerCache.map { Pair(it.key, it.value.value) }
                .plus(neighbors.map { Pair(it.key, it.value.peer) })
                .toMap()

    final override val knownChannels : Set<StaticChannelInformation>
        get() = channelCache.map { it.value }
                .plus(neighbors.map { it.value.channel })
                .toSet()

    protected var currentCycle: Int = 0
    protected val neighbors = mutableMapOf<WalletAddress, Neighbor>()

    private val peerCache = mutableMapOf<WalletAddress, WithLastAccess<Peer>>()
    private val channelCache = mutableSetOf<WithLastAccess<StaticChannelInformation>>()

    init {
        initialKnownPeers.forEach { addToPeerCache(it.value) }
        for (ch in initialKnownChannels) {
            if (ch.hasWallet(self.walletAddress)) {
                // Own channel -> new neighbor
                val neighborWallet = ch.otherWallet(self.walletAddress)
                addOrUpdateChannelInternal(ch, initialKnownPeers.getValue(neighborWallet))
            } else {
                addToChannelCache(ch)
            }
        }
    }

    protected fun addToPeerCache(peer: Peer){
        peerCache[peer.walletAddress] = WithLastAccess(currentCycle, peer)
        if (peerCache.size > cacheSize) {
            // Remove oldest cache item
            val elem = peerCache.values.sortedBy { it.lastAccessCycle }.first()
            peerCache.remove(elem.value.walletAddress)
        }
    }

    protected fun addToChannelCache(channel: StaticChannelInformation) {
        // Channels to neighbors shouldn't be in the cache
        if (!channel.hasWallet(self.walletAddress)) {
            val entry = channelCache.singleOrNull { it.value.hasWallets(channel.fromWallet, channel.toWallet) }
            if (entry == null) {
                channelCache.add(WithLastAccess(currentCycle, channel))
            } else {
                entry.lastAccessCycle = currentCycle
            }
            if (channelCache.size > cacheSize) {
                // Remove oldest cache item
                val elem = channelCache.sortedBy { it.lastAccessCycle }.first()
                channelCache.remove(elem)
            }
        }
    }

    final override suspend fun addOrUpdateChannel(channel: StaticChannelInformation, peer: Peer) {
        addOrUpdateChannelInternal(channel, peer)
        onChannelAddedOrUpdated(channel, peer)
    }

    private fun addOrUpdateChannelInternal(channel: StaticChannelInformation, peer: Peer) {
        val neighborWallet = channel.otherWallet(self.walletAddress)
        if (peer.walletAddress != neighborWallet) {
            throw NodeException("Channel and peer do not match.")
        }
        val neighbor = neighbors[neighborWallet]
        if (neighbor == null) {
            // Add channel
            neighbors[neighborWallet] = Neighbor(peer, channel)
            // Remove channel to neighbor from cache
            channelCache.removeIf { it.value.hasWallets(channel.fromWallet, channel.toWallet) }
        } else {
            // Update channel
            neighbor.channel = channel
        }
        addToPeerCache(peer)
    }

    final override suspend fun removeChannel(peer: WalletAddress) {
        if (peer == self.walletAddress) {
            throw NodeException("Cannot remove channel to itself. (${self.walletAddress})")
        }
        if (!neighbors.containsKey(peer)) {
            throw NodeException("Cannot remove channel from ${self.walletAddress} to $peer because it doesn't exist.")
        }
        neighbors.remove(peer)
        onChannelRemoved(peer)
    }

    /**
     * Implementation of findPeer using the cache and findRoutes
     */
    override suspend fun findPeer(target: WalletAddress): Peer {
        if (!knownPeers.containsKey(target)) {
            log.warn("findPeer: Target peer $target not known.")
            findRoutes(target) // invoke route finding; this resolves also the peer (REMARK: Ensure that findRoute doesn't use findPeer)
        }
        val peer = knownPeers[target]
        if (peer != null) {
            addToPeerCache(peer)
            return peer
        } else {
            throw NodeException("findPeer: target $target not found.")
        }
    }

    /**
     * Implementation of findRoute using findRoutes
     */
    override suspend fun findRoute(targets: List<WalletAddress>): Route {
        val routes = findRoutes(targets.last())
        for (route in routes) {
            val nodes = route.channels.map { it.toWallet }.toList()
            if (targets == nodes)
                return route
        }
        throw NodeException("findRoute: Predefined route to ${targets.last()} not found.")
    }

    final override suspend fun cycle(cycleNumber: Int) {
        currentCycle = cycleNumber
        onCycle()
    }

    open suspend fun onChannelAddedOrUpdated(channel: StaticChannelInformation, peer: Peer) {}
    open suspend fun onChannelRemoved(peer: WalletAddress) {}
    open suspend fun onCycle() {}

    class Neighbor(
            val peer: Peer,
            var channel: StaticChannelInformation
    ) {
        fun myLiquidity() : Double = channel.liquidity(channel.otherWallet(peer.walletAddress))
        fun neighborLiquidity() : Double = channel.liquidity(peer.walletAddress)
    }

    class WithLastAccess<T>(
            var lastAccessCycle: Int,
            var value: T
    )

    override fun report(topic: String, target: WalletAddress, successful: Boolean) {}
}
