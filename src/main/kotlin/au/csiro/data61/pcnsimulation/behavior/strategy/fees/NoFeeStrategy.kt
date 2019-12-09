package au.csiro.data61.pcnsimulation.behavior.strategy.fees

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.strategy.FeeStrategy

/**
 * Basic implementation of a fee strategy, that does not charge any fees.
 */
class NoFeeStrategy : FeeStrategy {

    /**
     * Does not charge any fees
     */
    override fun determine(channelPartner: WalletAddress): ChannelFee {
        return ChannelFee.ZERO
    }
}
