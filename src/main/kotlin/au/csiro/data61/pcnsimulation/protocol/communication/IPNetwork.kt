package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress

/**
 * Simulates the internet and observes all communication between nodes
 */
interface IPNetwork {

    /**
     * Create a new socket for the network with the given [IPAddress].
     */
    fun createSocket(ipAddress: IPAddress) : IPSocket

    /**
     * Subscribe to all packets send over the network (e.g. for logging).
     */
    fun subscribe(handler: (IPPacket) -> Unit)
}
