package au.csiro.data61.pcnsimulation.protocol.strategy

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee

/**
 * Determines how fees for a multi-channel transaction are determined. Fees might follow a fixed-rate or adapt
 * dynamically according to a list of criteria.
 */
interface FeeStrategy {

    /**
     * Determines the current fee for a payment of the given amount under which the node is willing to forward the
     * payment on the given channel.
     */
    fun determine(channelPartner: WalletAddress): ChannelFee
}
