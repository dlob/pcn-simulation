package au.csiro.data61.pcnsimulation.protocol.message.response

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import au.csiro.data61.pcnsimulation.protocol.message.request.CloseChannelRequest

/**
 * [Response] to a [CloseChannelRequest]. If [transaction] == null, then the request is declined, otherwise it is
 * accepted and returns the latest commitment transaction. Channels can be closed unilaterally and do not require
 * consensus. Thus, the requesting node can decide to close a channel without the explicit consent of the responder.
 */
data class CloseChannelResponse(
        val transaction: Transaction?
) : Response()
