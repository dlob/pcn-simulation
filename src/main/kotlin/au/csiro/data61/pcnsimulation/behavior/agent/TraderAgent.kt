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
 * Trader
 * Business like retail stores with fixed opening hours are represented by the trader role.
 *
 * max-success
 * fast-payments-if-possible
 * max-profit
 * hub-partner[1,8]
 */
class TraderAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.TRADER, node, sizeCompensation) {

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
        hubPartnerCreateNewChannel(1, 8)
        maxSuccessChannelRequired()
        maxSuccessCloseUnprofitableChannel(cycle, maxProfitChannelCloseWeight())
        maxSuccessCreateBeneficialChannel(8)
        node.strategy.fee = maxProfitAdjustFees()
    }
}
