package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress
import java.util.*

/**
 * A network packet that is sent from a [sender] to a [receiver].
 */
data class IPPacket(
        /**
         * Internet address of the sending node
         */
        val sender: IPAddress,

        /**
         * Internet address of the receiving node
         */
        val receiver: IPAddress,

        /**
         * Payload of the packet
         */
        val payload: Any,

        /**
         * Unique packet identifier, which responses use to refer to to their initiating request.
         */
        val id: String = UUID.randomUUID().toString().take(8),

        /**
         * (Optional) unique packet identifier of the packet that preceded the current packet.
         */
        val respondingToId: String? = null
)
