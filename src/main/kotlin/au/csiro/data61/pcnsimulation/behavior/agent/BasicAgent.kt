package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.FullDisclosureStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.OnePercentFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.RelayAllPaymentsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.node.NodeException
import kotlinx.coroutines.runBlocking

/**
 * Basic implementation of an agent.
 */
class BasicAgent(
        override val node: Node
) : Agent {

    companion object {
        val log by logger()
    }

    init {
        runBlocking {
            // Initialize strategies
            node.strategy.channel = ApproveAllChannelsStrategy()
            node.strategy.information = FullDisclosureStrategy()
            node.strategy.fee = OnePercentFeeStrategy()
            node.strategy.route = CheapestRouteStrategy()
            node.strategy.paymentRelaying = RelayAllPaymentsStrategy()
            // Go on-line permanently
            node.goOnline()
        }
    }

    override suspend fun addPayment(from: WalletAddress, to: WalletAddress, amount: Double, urgent: Boolean) {
        try {
            if (node.channels.any { it.otherWallet(from) == to }) {
                node.makeChannelTransaction(to, amount)
            } else {
                node.makeMultiChannelTransaction(to, amount)
            }
        } catch (ex: NodeException) {
            log.warn(ex.message)
        }
    }

    override suspend fun cycle(cycle: Int) {}
}
