package au.csiro.data61.pcnsimulation.behavior.strategy.disclosure

import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation
import au.csiro.data61.pcnsimulation.protocol.strategy.DisclosureStrategy

class PartialDisclosureStrategy : DisclosureStrategy {
    /**
     * Discloses 9.1% of the liquidity available in a channel.
     */
    override fun disclose(channel: DynamicChannelInformation): DynamicChannelInformation {
        return channel.copy(liquidity = channel.liquidity / 11)
    }
}
