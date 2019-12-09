package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.FullDisclosureStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.OnePercentFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.RelayAllPaymentsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.protocol.message.request.CloseChannelRequest
import au.csiro.data61.pcnsimulation.protocol.message.request.OpenChannelRequest
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.protocol.strategy.ApprovalResult
import au.csiro.data61.pcnsimulation.protocol.strategy.ChannelStrategy
import au.csiro.data61.pcnsimulation.protocol.strategy.PaymentRelayingStrategy
import kotlinx.coroutines.runBlocking

/**
 * Faulty User
 * A faulty user is a consumer with a pseudo-random possibility (5%) that functionality fails.
 *
 * max-success
 * fast-payments-only
 * balanced-costs
 * hub-partner[1,5]
 */
class FaultyUserAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.FAULTY_USER, node, sizeCompensation) {

    val failingPercents = 5

    init {
        runBlocking {
            // Initialize strategies
            node.strategy.channel = object : ChannelStrategy {
                override fun accept(node: Node, request: OpenChannelRequest): ApprovalResult {
                    return if (random.nextInt(100) < failingPercents) {
                        ApprovalResult.IGNORE
                    } else {
                        ApprovalResult.APPROVE
                    }
                }
                override fun accept(node: Node, request: CloseChannelRequest): ApprovalResult {
                    return if (random.nextInt(100) < failingPercents) {
                        ApprovalResult.IGNORE
                    } else {
                        ApprovalResult.APPROVE
                    }
                }
            }
            node.strategy.information = FullDisclosureStrategy()
            node.strategy.fee = OnePercentFeeStrategy()
            node.strategy.paymentRelaying = object : PaymentRelayingStrategy {
                override fun accept(incomingWallet: WalletAddress, outgoingWallet: WalletAddress): ApprovalResult {
                    return if (random.nextInt(100) < failingPercents) {
                        ApprovalResult.IGNORE
                    } else {
                        ApprovalResult.APPROVE
                    }
                }
            }
            node.strategy.route = CheapestRouteStrategy { r -> setLastRoute(r.routes.first()) }
        }
    }

    override suspend fun addPayment(from: WalletAddress, to: WalletAddress, amount: Double, urgent: Boolean) {
        if (random.nextInt(100) < failingPercents) {
            log.info("Faulty user $from failed to schedule payment.")
        } else {
            fastPaymentOnly(from, to, amount)
        }
    }

    override suspend fun cycle(cycle: Int) {
        refreshChannelEvaluation(cycle)
        hubPartnerCreateNewChannel(1, 5)
        maxSuccessChannelRequired()
        maxSuccessCloseUnprofitableChannel(cycle, balancedCostsChannelCloseWeight())
        maxSuccessCreateBeneficialChannel(5)
    }
}
