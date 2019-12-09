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
 * Heavy Consumer
 * A heavy consumer is very committed to cryptocurrencies and accepts the fallback to on-chain transactions.
 *
 * max-success
 * fast-payments-if-possible
 * balanced-costs
 * hub-partner[1,∞]
 */
class HeavyConsumerAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.HEAVY_CONSUMER, node, sizeCompensation) {

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
        hubPartnerCreateNewChannel(1, Int.MAX_VALUE)
        maxSuccessChannelRequired()
        maxSuccessCloseUnprofitableChannel(cycle, balancedCostsChannelCloseWeight())
        maxSuccessCreateBeneficialChannel(Int.MAX_VALUE)
    }
}
