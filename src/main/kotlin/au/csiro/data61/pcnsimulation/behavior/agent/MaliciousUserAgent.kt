package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.BoastStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.NoFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.IgnorePaymentsStrategy
import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.protocol.message.request.CloseChannelRequest
import au.csiro.data61.pcnsimulation.protocol.message.request.OpenChannelRequest
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.protocol.strategy.ApprovalResult
import au.csiro.data61.pcnsimulation.protocol.strategy.ChannelStrategy
import kotlinx.coroutines.runBlocking

/**
 * Malicious User
 * This user is generally uncooperative and aims to harm others by locking up funds in unusable channels
 *
 * no-payment
 * many-channels
 * big-channels
 */
class MaliciousUserAgent (
        node: Node,
        sizeCompensation: Double
) : BaseAgent(AgentRole.MALICIOUS_USER, node, sizeCompensation) {

    init {
        runBlocking {
            // Initialize strategies
            node.strategy.channel =  object : ChannelStrategy {
                override fun accept(node: Node, request: OpenChannelRequest): ApprovalResult {
                    return ApprovalResult.APPROVE
                }
                override fun accept(node: Node, request: CloseChannelRequest): ApprovalResult {
                    return ApprovalResult.IGNORE
                }
            }
            node.strategy.information = BoastStrategy()
            node.strategy.fee = NoFeeStrategy()
            node.strategy.paymentRelaying = IgnorePaymentsStrategy()
        }
    }

    override suspend fun addPayment(from: WalletAddress, to: WalletAddress, amount: Double, urgent: Boolean) {
        // payments are refused
        log.info("Malicious user $from ignores scheduled payment.")
    }

    override suspend fun cycle(cycle: Int) {
        refreshChannelEvaluation(cycle)
        bigChannelsCreate(manyChannelsMaxChannelCount(), listOf(0.1), listOf(16.0, 8.0, 4.0, 2.0, 1.0, 0.5))
    }
}
