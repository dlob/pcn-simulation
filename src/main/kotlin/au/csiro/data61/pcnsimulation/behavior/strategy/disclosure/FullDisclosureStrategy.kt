package au.csiro.data61.pcnsimulation.behavior.strategy.disclosure

import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation
import au.csiro.data61.pcnsimulation.protocol.strategy.DisclosureStrategy

/**
 * Most basic implementation of a disclosure strategy that discloses all available information.
 */
class FullDisclosureStrategy : DisclosureStrategy {
    /**
     * Discloses the liquidity available in a channel.
     */
    override fun disclose(channel: DynamicChannelInformation): DynamicChannelInformation {
        return channel.copy(liquidity = channel.liquidity)
    }
}
