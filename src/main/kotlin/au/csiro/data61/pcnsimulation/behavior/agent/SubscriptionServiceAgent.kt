package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.FullDisclosureStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.OnePercentFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.RelayAllPaymentsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.protocol.node.Node
import kotlinx.coroutines.runBlocking

/**
 * Subscription Service
 * A node of a subscription service receives regular payments from different customers.
 *
 * balanced-costs
 * hub-partner[1,8]
 */
class SubscriptionServiceAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.SUBSCRIPTION_SERVICE, node, sizeCompensation) {

    init {
        runBlocking {
            // Initialize strategies
            node.strategy.channel = ApproveAllChannelsStrategy()
            node.strategy.information = FullDisclosureStrategy()
            node.strategy.fee = OnePercentFeeStrategy()
            node.strategy.paymentRelaying = RelayAllPaymentsStrategy()
            node.strategy.route = CheapestRouteStrategy { r -> setLastRoute(r.routes.first()) }
        }
    }

    override suspend fun addPayment(from: WalletAddress, to: WalletAddress, amount: Double, urgent: Boolean) {
        fastPaymentIfPossible(from, to, amount)
    }

    override suspend fun cycle(cycle: Int) {
        refreshChannelEvaluation(cycle)
        hubPartnerCreateNewChannel(1, 8)
    }
}
