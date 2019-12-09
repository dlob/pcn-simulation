package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.configuration.Distribution
import au.csiro.data61.pcnsimulation.configuration.copy
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.blockchain.BlockchainException
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.protocol.node.NodeException
import au.csiro.data61.pcnsimulation.protocol.node.routing.Route
import au.csiro.data61.pcnsimulation.protocol.strategy.FeeStrategy
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

abstract class BaseAgent(
        role: AgentRole,
        final override val node: Node,
        val sizeCompensation: Double
) : Agent {

    companion object {
        val log by logger()
    }

    val random = Random(node.name.hashCode() + 1)
    val channelFundingDistribution: Distribution = role.channelFundingDistribution.copy(node.name.hashCode() + 2L)
    val paymentHistory: MutableList<PaymentHistoryEntry> = mutableListOf()
    val channelEvaluation: MutableSet<ChannelEvaluationEntry> = mutableSetOf()

    fun historyAddSuccessfulPayment(from: WalletAddress, to: WalletAddress, amount: Double, route: Route) {
        paymentHistory.add(PaymentHistoryEntry(from, to, amount, route, true))
    }

    fun historyAddFailedPayment(from: WalletAddress, to: WalletAddress, amount: Double) {
        paymentHistory.add(PaymentHistoryEntry(from, to, amount, null, false))
    }

    fun refreshChannelEvaluation(cycle: Int) {
        val refreshChannels = mutableSetOf<ChannelEvaluationEntry>()
        val removeChannels = channelEvaluation.toMutableSet()
        for (c in node.channels) {
            val ce = channelEvaluation.singleOrNull { it.channel === c }
            if (ce == null) {
                // add new channel
                channelEvaluation.add(ChannelEvaluationEntry(
                        c,
                        setOf(c.fromWallet, c.toWallet).single { it != node.walletAddress },
                        c.fromBalance() + c.toBalance(),
                        c.fundingTransaction.balance(node.walletAddress),
                        0,
                        0.0,
                        c.balance(node.walletAddress),
                        cycle
                ))
            } else {
                removeChannels.remove(ce)
                refreshChannels.add(ce)
            }
        }
        // refresh channels
        for (ce in refreshChannels) {
            val newBalance = ce.channel.balance(node.walletAddress)
            if (newBalance != ce.lastBalance) {
                ce.usageAmount += abs(ce.lastBalance - newBalance)
                ce.usageCount++
                ce.lastBalance = newBalance
            }
        }
        // remove closed channels
        for (ce in removeChannels) {
            channelEvaluation.remove(ce)
        }
    }

    data class PaymentHistoryEntry (
            val from: WalletAddress,
            val to: WalletAddress,
            val amount: Double,
            val route: Route?,
            val success: Boolean
    )

    class ChannelEvaluationEntry (
            val channel: TransactionChannel,
            val to: WalletAddress,
            val capacity: Double,
            val funding: Double,
            var usageCount: Int,
            var usageAmount: Double,
            var lastBalance: Double,
            val creationCycle: Int
    )

    /**
     * max-success
     * (x) ensure at least a channel
     * (x) close unused or unbalanced channels
     * (x) create channels that are beneficial (based on history; often visited)
     */
    suspend fun maxSuccessChannelRequired() {
        if (node.channels.isNotEmpty()) return
        val partners = node.channels.map { c -> c.otherWallet(node.walletAddress) }.toSet()
        val peers = node.router.knownPeers.values
                .filter { !partners.contains(it.walletAddress) && it.walletAddress != node.walletAddress }
                .map { Pair(it, random.nextDouble()) }
                .sortedBy { it.second }
                .toList()
        if (!node.socket.isConnected) node.goOnline()
        for (p in peers) {
            try {
                node.openChannel(
                        p.first.walletAddress,
                        channelFundingDistribution.sample(),
                        channelFundingDistribution.sample()
                )
                log.info("BEH|max-success: opened channel from ${node.walletAddress} to ${p.first.walletAddress}")
                break // channel established
            } catch (ex: NodeException) {
                log.warn(ex.message)
                // Ignore
            }
        }
    }

    suspend fun maxSuccessCloseUnprofitableChannel(cycle: Int, baseWeight: Double = 1.0) {
        val unprofitableChannel = channelEvaluation.asSequence()
                .map {
                    val channelUsageCount = it.usageCount.coerceAtLeast(1).toDouble() // Assume at least one usage, otherwise the channel is closed too early.
                    val channelAge = cycle - it.creationCycle
                    val cycleUtilization = (channelUsageCount * sizeCompensation / channelAge).coerceAtMost(1.0) // [0; 1], 0: channel not used, 1: ideal usage (every cycle)
                    val fundingUtilization = (it.usageAmount / it.funding).coerceAtMost(1.0) // [0; 1], 0: channel not used, 1: amount of initial funding has been transferred
                    val balancing = abs(it.lastBalance / it.capacity - 0.5) / -0.5 + 1 // [0; 1], 0: unbalanced, 1: balanced
                    Pair(it, (cycleUtilization * 0.4 + fundingUtilization * 0.3 + balancing * 0.3) * baseWeight)
                }
                .minBy { it.second }
        if (unprofitableChannel != null && unprofitableChannel.second < 0.4) {
            // close channel
            if (!node.socket.isConnected) node.goOnline()
            try {
                node.closeChannel(unprofitableChannel.first.to)
                channelEvaluation.remove(unprofitableChannel.first)
                log.info("BEH|max-success: closed channel from ${node.walletAddress} to ${unprofitableChannel.first.to} (${unprofitableChannel.second})")
            } catch (ex: NodeException) {
                log.warn(ex.message)
                // Ignore
            }
        }
    }

    suspend fun maxSuccessCreateBeneficialChannel(maxChannelCount: Int) {
        if (node.channels.size >= maxChannelCount) return
        val partners = node.channels.map { c -> c.otherWallet(node.walletAddress) }.toSet()
        val candidates = paymentHistory
                .asSequence()
                .mapNotNull { it.route?.peers?.values }
                .flatten()
                .groupBy { it.walletAddress }
                .filter { it.key != node.walletAddress && !partners.contains(it.key) }
                .map { Pair(it.key, it.value.size) }
                .sortedByDescending { it.second }
                .toList()
        if (!node.socket.isConnected) node.goOnline()
        for (can in candidates) {
            try {
                node.openChannel(
                        can.first,
                        channelFundingDistribution.sample(),
                        channelFundingDistribution.sample()
                )
                log.info("BEH|max-success: opened channel from ${node.walletAddress} to ${can.first}")
                break // channel established
            } catch (ex: NodeException) {
                log.warn(ex.message)
                // Ignore
            }
        }
    }

    /**
     * fast-payments-only
     * (x) only make channel payments or drop it
     */
    private var lastRoute: Route? = null

    fun setLastRoute(r: Route) {
        lastRoute = r
    }

    suspend fun fastPaymentOnly(from: WalletAddress, to: WalletAddress, amount: Double) {
        if (!node.socket.isConnected) node.goOnline()
        try {
            if (node.channels.any { it.otherWallet(from) == to }) {
                node.makeChannelTransaction(to, amount)
            } else {
                node.makeMultiChannelTransaction(to, amount)
                historyAddSuccessfulPayment(from, to, amount, lastRoute!!)
            }
        } catch (ex: NodeException) {
            log.warn(ex.message)
            historyAddFailedPayment(from, to, amount)
            // no fallback to on-chain transaction: ignore
        }
    }

    /**
     * fast-payment-if-possible
     * (x) try to make payment in this order: pcn, bc, nop
     */
    suspend fun fastPaymentIfPossible(from: WalletAddress, to: WalletAddress, amount: Double) {
        if (!node.socket.isConnected) node.goOnline()
        try {
            if (node.channels.any { it.otherWallet(from) == to }) {
                node.makeChannelTransaction(to, amount)
            } else {
                node.makeMultiChannelTransaction(to, amount)
                historyAddSuccessfulPayment(from, to, amount, lastRoute!!)
            }
        } catch (ex: NodeException) {
            log.warn(ex.message)
            historyAddFailedPayment(from, to, amount)
            // fallback to on-chain transaction
            try {
                node.blockchain.transfer(from, to, amount)
                log.info("${node.name}: Fallback to on-chain transaction successful for payment: from=$from, to=$to, amount=$amount, fee=${node.blockchain.fee}")
            } catch (ex2: BlockchainException) {
                log.warn("${node.name}: Fallback to on-chain transaction failed for payment: from=$from, to=$to, amount=$amount, fee=${node.blockchain.fee}")
            }
        }
    }

    /**
     * no-payment
     * (x) do not make any payments -> empty addPayment method
     * (x) ignore payment relaying requests -> IgnorePaymentsStrategy
     */

    /**
     * min-costs
     * (x) cheapest payment route -> CheapestRouteStrategy
     * (x) keep channels open as long as possible
     * (x) go offline as soon as possible after payment
     */
    var onlineCycleCount: Int = 0

    suspend fun minCostsGoOffline() {
        if (node.socket.isConnected) {
            // min-costs: go offline after payment
            onlineCycleCount++
            if (onlineCycleCount > 2) {
                node.goOffline()
                log.info("BEH|min-costs: go offline ${node.walletAddress}")
                onlineCycleCount = 0
            }
        } else {
            onlineCycleCount = 0
        }
    }

    fun minCostsChannelCloseWeight(): Double = 2.0

    /**
     * balanced-costs
     * (x) cheapest payment route -> CheapestRouteStrategy
     * (x) keep channels open as long as possible
     * (x) be attractive for relaying payments -> OnePercentFeeStrategy
     */
    fun balancedCostsChannelCloseWeight(): Double = 1.8

    /**
     * max-profit
     * (x) maximize fees for relaying payments (adjust fees based on the channel balance)
     */
    fun maxProfitChannelCloseWeight(): Double = 2.0

    fun maxProfitAdjustFees(): FeeStrategy = object : FeeStrategy {
        override fun determine(channelPartner: WalletAddress): ChannelFee {
            val channel = node.channels.single { c -> setOf(c.fromWallet, c.toWallet).contains(channelPartner) }
            val capacity = channel.fromBalance() + channel.toBalance()
            val nodeBalance = if (channel.fromWallet == node.walletAddress) {
                channel.fromBalance()
            } else {
                channel.toBalance()
            }
            val balancing = nodeBalance / capacity // 0 unbalanced (zero liquidity); 0.5 balanced; 1 unbalanced (full liquidity)
            // goal: towards zero liquidity -> higher fees; towards full liquidity -> lower fees
            // rate interval: [0.95, 1.55]
            // fixed interval: [0, 0.5]
            return ChannelFee(1.55 - 0.6 * balancing, 0.5 - 0.5 * balancing)
        }
    }

    /**
     * hub-partner
     * (x) create channels to hubs until a specific amount of channels is reached
     */
    suspend fun hubPartnerCreateNewChannel(minHubChannelCount: Int, maxChannelCount: Int) {
        val hubPartnerCount = node.channels
                .map { c -> c.otherWallet(node.walletAddress) }
                .map { w -> node.router.knownChannels.filter { c -> c.hasWallet(w) }.size }
                .filter { it > 6 }
                .size
        if (hubPartnerCount >= minHubChannelCount) return
        if (node.channels.size >= maxChannelCount) return
        // try to create a new channel
        val partners = node.channels.map { c -> c.otherWallet(node.walletAddress) }.toSet()
        val hubNodes = node.router.knownPeers.values
                .asSequence()
                .filter { n -> !partners.contains(n.walletAddress) && n.walletAddress != node.walletAddress }
                .map { n -> Pair(n, node.router.knownChannels.filter { c -> c.hasWallet(n.walletAddress) }.size) }
                .filter { it.second > 6 }
                .sortedByDescending { it.second }
                .toList()
        if (!node.socket.isConnected) node.goOnline()
        for (h in hubNodes) {
            try {
                node.openChannel(
                        h.first.walletAddress,
                        channelFundingDistribution.sample(),
                        channelFundingDistribution.sample()
                )
                log.info("BEH|hub-partner: opened channel from ${node.walletAddress} to ${h.first.walletAddress}")
                break // channel established
            } catch (ex: NodeException) {
                log.warn(ex.message)
                // Ignore
            }
        }
    }

    /**
     * many-channels
     * (x) create arbitrary many channels -> ApproveAllChannelsStrategy
     */
    fun manyChannelsMaxChannelCount(): Int = Int.MAX_VALUE

    /**
     * big-channels
     * (x) create arbitrary big channels
     */
    suspend fun bigChannelsCreate(maxChannelCount: Int, ownFundingScales: List<Double>, parnterFundingScales: List<Double>) {
        val partners = node.channels.map { c -> c.otherWallet(node.walletAddress) }.toSet()
        val peers = node.router.knownPeers.values
                .filter { !partners.contains(it.walletAddress) && it.walletAddress != node.walletAddress }
                .toList()
        if (!node.socket.isConnected) node.goOnline()
        val scales = ownFundingScales.flatMap { o -> parnterFundingScales.map { p -> Pair(o, p) } }
        for (p in peers) {
            val fOwn = channelFundingDistribution.sample()
            val fPartner = channelFundingDistribution.sample()
            for (s in scales) {
                try {
                    node.openChannel(p.walletAddress, fOwn * s.first, fPartner * s.second)
                    log.info("BEH|big-channels: opened channel from ${node.walletAddress} to ${p.walletAddress}")
                    if (node.channels.size >= maxChannelCount) return
                    break // next peer
                } catch (ex: NodeException) {
                    log.warn(ex.message)
                    // Ignore
                }
            }
        }
    }
}
