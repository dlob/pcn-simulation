package au.csiro.data61.pcnsimulation.protocol.strategy

import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation

/**
 * Determines how a node discloses information of itself and its channels. A node may decide to
 * hide some information from requesting peers, depending on a self-chosen list of criteria.
 */
interface DisclosureStrategy {
    /**
     * Returns current channel information to share with a requesting peer.
     */
    fun disclose(channel: DynamicChannelInformation): DynamicChannelInformation
}
