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
 * Passive Consumer
 * This user is offline most of the time and makes transactions only if they are fast.
 *
 * max-success
 * fast-payments-only
 * min-costs
 * hub-partner[1,3]
 */
class PassiveConsumerAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.PASSIVE_CONSUMER, node, sizeCompensation) {

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
        fastPaymentOnly(from, to, amount)
    }

    override suspend fun cycle(cycle: Int) {
        refreshChannelEvaluation(cycle)
        hubPartnerCreateNewChannel(1, 3)
        maxSuccessChannelRequired()
        maxSuccessCloseUnprofitableChannel(cycle, minCostsChannelCloseWeight())
        maxSuccessCreateBeneficialChannel(3)
        minCostsGoOffline()
    }
}
