package au.csiro.data61.pcnsimulation.protocol.strategy

import au.csiro.data61.pcnsimulation.protocol.message.request.CloseChannelRequest
import au.csiro.data61.pcnsimulation.protocol.message.request.OpenChannelRequest
import au.csiro.data61.pcnsimulation.protocol.node.Node

/**
 * Determines how a node responds to a [OpenChannelRequest]. The strategy does not need to consider
 * the validity of a decision, e.g. whether enough funds are available. Instead, it  only needs to express a stance
 * towards a positive or negative decision. The feasibility of a decision is then handled on the protocol level.
 */
interface ChannelStrategy {

    /**
     * Returns the decision, whether the request to open a channel should be accepted or declined.
     */
    fun accept(node: Node, request: OpenChannelRequest): ApprovalResult

    /**
     * Returns the decision, whether the request to close a channel should be accepted or declined.
     */
    fun accept(node: Node, request: CloseChannelRequest): ApprovalResult
}