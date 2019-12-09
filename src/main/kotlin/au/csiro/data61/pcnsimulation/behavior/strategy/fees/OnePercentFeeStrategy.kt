package au.csiro.data61.pcnsimulation.behavior.strategy.fees

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.strategy.FeeStrategy

/**
 * Basic implementation of a fee strategy, that charges a fixed 1% rate.
 */
class OnePercentFeeStrategy : FeeStrategy {

    /**
     * Charges 1% of the amount
     */
    override fun determine(channelPartner: WalletAddress): ChannelFee {
        return ChannelFee(1.01, 0.0)
    }
}
