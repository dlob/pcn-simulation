package au.csiro.data61.pcnsimulation.protocol.message.response

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.message.request.OpenChannelRequest

/**
 * [Response] to a [OpenChannelRequest] indicating the decision of opening a new channel with the requesting peer.
 * If [fundingTransaction] == null, then the request is declined, otherwise it is accepted and returns the identical
 * specifications than the requester sent.
 */
data class AcceptChannelResponse(
        val fundingTransaction: FundingTransaction?
) : Response()
