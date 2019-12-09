package au.csiro.data61.pcnsimulation.protocol.message.request

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import au.csiro.data61.pcnsimulation.protocol.message.response.CloseChannelResponse

/**
 * Asks a peer to close the specified transaction channel.
 *
 * Expects a [CloseChannelResponse]
 */
data class CloseChannelRequest(
        val transaction: Transaction
) : Request()
