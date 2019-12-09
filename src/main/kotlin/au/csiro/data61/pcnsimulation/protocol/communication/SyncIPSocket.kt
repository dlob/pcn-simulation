package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress

class SyncIPSocket(override val ipAddress: IPAddress,
                   val forwarder: suspend (IPPacket) -> Unit) : IPSocket {
    override var isConnected = true
    private val receiveCallbacks = mutableListOf<suspend (IPPacket) -> Unit>()

    override suspend fun notify(receiver: IPAddress, payload: Any) {
        if (!isConnected) throw SocketClosedException(ipAddress)
        forwarder(IPPacket(ipAddress, receiver, payload))
    }

    private val responses = mutableMapOf<String, Any>()
    private val invalidResponse = object {}

    override suspend fun request(receiver: IPAddress, payload: Any): Any {
        if (!isConnected) throw SocketClosedException(ipAddress)
        val packet = IPPacket(ipAddress, receiver, payload)
        responses[packet.id] = invalidResponse
        forwarder(packet)
        val response = responses.remove(packet.id)!!
        if (response === invalidResponse) {
            throw RequestUnansweredException(ipAddress, receiver, payload)
        } else {
            return response
        }
    }

    override suspend fun request(receiver: IPAddress, payload: Any, timeoutMilliseconds: Long): Any {
        return request(receiver, payload)
    }

    override suspend fun respond(receiver: IPAddress, requestId: String, payload: Any) {
        if (!isConnected) throw SocketClosedException(ipAddress)
        forwarder(IPPacket(ipAddress, receiver, payload, respondingToId = requestId))
    }

    override fun registerReceiveCallback(callback: suspend (IPPacket) -> Unit) {
        receiveCallbacks.add(callback)
    }

    suspend fun forward (packet: IPPacket) {
        if (isConnected) {
            if (packet.respondingToId != null && responses.containsKey(packet.respondingToId)) {
                responses[packet.respondingToId] = packet.payload
            } else {
                receiveCallbacks.forEach { it(packet) }
            }
        } else {
            throw PacketUndeliverableException(packet)
        }
    }

    override suspend fun connect() {
        isConnected = true
    }
    override suspend fun close() {
        isConnected = false
    }
}
