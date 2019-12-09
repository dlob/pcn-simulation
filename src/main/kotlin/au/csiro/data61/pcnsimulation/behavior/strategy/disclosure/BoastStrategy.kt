package au.csiro.data61.pcnsimulation.behavior.strategy.disclosure

import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation
import au.csiro.data61.pcnsimulation.protocol.strategy.DisclosureStrategy

class BoastStrategy : DisclosureStrategy {
    override fun disclose(channel: DynamicChannelInformation): DynamicChannelInformation {
        return channel.copy(liquidity = channel.liquidity * 10.0)
    }
}
