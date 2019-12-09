package au.csiro.data61.pcnsimulation.behavior.strategy.channel

import au.csiro.data61.pcnsimulation.protocol.message.request.CloseChannelRequest
import au.csiro.data61.pcnsimulation.protocol.message.request.OpenChannelRequest
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.protocol.strategy.ApprovalResult
import au.csiro.data61.pcnsimulation.protocol.strategy.ChannelStrategy

/**
 * Most basic implementation of a channel strategy that always accepts requests.
 */
class ApproveAllChannelsStrategy : ChannelStrategy {

    /**
     * Accepts a request to open a channel under all conditions
     */
    override fun accept(node: Node, request: OpenChannelRequest): ApprovalResult {
        return ApprovalResult.APPROVE
    }

    /**
     * Accepts a request to close a channel under all conditions
     */
    override fun accept(node: Node, request: CloseChannelRequest): ApprovalResult {
        return ApprovalResult.APPROVE
    }
}