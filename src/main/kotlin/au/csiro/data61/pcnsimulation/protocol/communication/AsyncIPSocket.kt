package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.util.CountingLatch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Asynchronous implementation of an [IPSocket], based on co-routines.
 */
class AsyncIPSocket(override val ipAddress: IPAddress,
                    private val sendChannel: Channel<Any>,
                    private val coroutineScope: CoroutineScope,
                    private val callbackCounter: CountingLatch) : IPSocket {

    companion object {
        private val log by logger()
    }

    override val isConnected: Boolean
        get() = connected.get()

    private val connected = AtomicBoolean(false)
    private val expectedResponses = mutableMapOf<String, Pair<(IPPacket) -> Unit, (IPNetworkException) -> Unit>>()

    override suspend fun notify(receiver: IPAddress, payload: Any) {
        if (!connected.get()) throw SocketClosedException(ipAddress)
        callbackCounter.increment()
        sendChannel.send(IPPacket(ipAddress, receiver, payload))
    }

    override suspend fun request(receiver: IPAddress, payload: Any) : Any = suspendCancellableCoroutine { cont ->
        if (!connected.get()) throw SocketClosedException(ipAddress)
        callbackCounter.increment()
        val packet = IPPacket(ipAddress, receiver, payload)
        expectedResponses[packet.id] = Pair<(IPPacket) -> Unit, (IPNetworkException) -> Unit>({
            cont.resume(it.payload)
        }, {
            cont.resumeWithException(it)
        })
        coroutineScope.launch {
            sendChannel.send(packet)
        }
    }

    override suspend fun request(receiver: IPAddress, payload: Any, timeoutMilliseconds: Long) : Any {
        try {
            return withTimeout(timeoutMilliseconds) {
                request(receiver, payload)
            }
        } catch (e: TimeoutCancellationException) {
            throw RequestUnansweredException(ipAddress, receiver, payload)
        }
    }

    override suspend fun respond(receiver: IPAddress, requestId: String, payload: Any) {
        if (!connected.get()) throw SocketClosedException(ipAddress)
        callbackCounter.increment()
        sendChannel.send(IPPacket(ipAddress, receiver, payload, respondingToId = requestId))
    }

    private val receiveCallbacks = mutableListOf<suspend (IPPacket) -> Unit>()
    private lateinit var receiveChannel: Channel<Any>
    private lateinit var receiveJob: Job

    override fun registerReceiveCallback(callback: suspend (IPPacket) -> Unit) {
        receiveCallbacks.add(callback)
    }

    override suspend fun connect() {
        receiveChannel = Channel(Channel.UNLIMITED)
        receiveJob = coroutineScope.launch {
            for (p in receiveChannel) {
                when (p) {
                    is PacketDeliveryFailedPacket -> {
                        val ex = PacketUndeliverableException(p.packet)
                        if (expectedResponses.containsKey(p.packet.id)) {
                            expectedResponses[p.packet.id]?.second?.invoke(ex)
                            expectedResponses.remove(p.packet.id)
                        } else {
                            log.warn("Packet undeliverable. ${p.packet.sender} -X-> ${p.packet.receiver} ${p.packet.payload.javaClass.name}", ex)
                        }
                    }
                    is IPPacket -> {
                        if (p.respondingToId == null) {
                            receiveCallbacks.forEach {
                                callbackCounter.increment()
                                coroutineScope.launch {
                                    it(p)
                                    callbackCounter.decrement()
                                }
                            }
                        } else if (expectedResponses.containsKey(p.respondingToId)) {
                            expectedResponses[p.respondingToId]?.first?.invoke(p)
                            expectedResponses.remove(p.respondingToId)
                        } else {
                            log.warn("Unexpected response received. ${p.sender} ---> ${p.receiver}", UnexpectedResponseReceivedException(p))
                        }
                    }
                }
                callbackCounter.decrement()
            }
        }
        sendChannel.send(ConnectSocketPacket(this, receiveChannel))
        connected.set(true)
    }

    override suspend fun close() {
        connected.set(false)
        sendChannel.send(CloseSocketPacket(this))
        // The [receiveChannel] is closed by the network upon reception of a [CloseSocketPacket].
        receiveJob.join()
    }

    /**
     * Packet that is send on connecting to the network.
     */
    data class ConnectSocketPacket(
            val socket: IPSocket,
            val channel: Channel<Any>
    )

    /**
     * Packet that is send on disconnecting from the network.
     */
    data class CloseSocketPacket(
            val socket: IPSocket
    )

    /**
     * Packet that is send from network if the receiver doesn't exist or isn't connected.
     */
    data class PacketDeliveryFailedPacket(
            val packet: IPPacket
    )
}
