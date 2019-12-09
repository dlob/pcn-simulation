package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress

class SyncIPNetwork : IPNetwork {
    private var forwardMap = mutableMapOf<IPAddress, SyncIPSocket>()
    private val subscribers = mutableListOf<(IPPacket) -> Unit>()

    override fun createSocket(ipAddress: IPAddress): IPSocket {
        val socket = SyncIPSocket(ipAddress) { packet ->
            subscribers.forEach { it(packet) }
            forwardMap[packet.receiver]!!.forward(packet)
        }
        forwardMap[ipAddress] = socket
        return socket
    }

    override fun subscribe(handler: (IPPacket) -> Unit) {
        subscribers.add(handler)
    }
}
