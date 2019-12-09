package au.csiro.data61.pcnsimulation.protocol.message.notification

import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.node.routing.Peer

/**
 * Informs peers about the opening or closing of a channel.
 */
data class ChannelUpdateNotification(
        val channel: StaticChannelInformation,
        val peer: Peer,
        val state: ChannelState
) : Notification() {

    enum class ChannelState {
        OPEN,
        CLOSED
    }
}
