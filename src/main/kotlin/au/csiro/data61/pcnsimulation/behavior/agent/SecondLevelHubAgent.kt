package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.FullDisclosureStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.RelayAllPaymentsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.protocol.node.Node
import kotlinx.coroutines.runBlocking

/**
 * Second Level Hub
 * The business model of this role is to connect different hubs.
 *
 * max-profit
 * hub-partner[1,∞]
 * big-channels
 */
class SecondLevelHubAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.SECOND_LEVEL_HUB, node, sizeCompensation) {

    init {
        runBlocking {
            // Initialize strategies
            node.strategy.channel = ApproveAllChannelsStrategy()
            node.strategy.information = FullDisclosureStrategy()
            node.strategy.fee = maxProfitAdjustFees()
            node.strategy.paymentRelaying = RelayAllPaymentsStrategy()
            node.strategy.route = CheapestRouteStrategy { r -> setLastRoute(r.routes.first()) }
        }
    }

    override suspend fun addPayment(from: WalletAddress, to: WalletAddress, amount: Double, urgent: Boolean) {
        fastPaymentIfPossible(from, to, amount)
    }

    override suspend fun cycle(cycle: Int) {
        refreshChannelEvaluation(cycle)
        hubPartnerCreateNewChannel(1, Int.MAX_VALUE)
        bigChannelsCreate(Int.MAX_VALUE, listOf(8.0, 4.0, 2.0, 1.0), listOf(8.0, 4.0, 2.0, 1.0))
        node.strategy.fee = maxProfitAdjustFees()
    }
}
