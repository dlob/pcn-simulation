package au.csiro.data61.pcnsimulation.protocol.message.request

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.message.response.AcceptChannelResponse

/**
 * Asks a peer to open a transaction channel with the specified properties. Depending on the nodes strategy, a peer
 * can accept and decline the channel.
 *
 * Expects a [AcceptChannelResponse]
 */
data class OpenChannelRequest(
        val fundingTransaction: FundingTransaction
) : Request()
