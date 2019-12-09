package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.util.CountingLatch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Asynchronous implementation of [IPNetwork], based on co-routines.
 * After [stop] is invoked, the network can be reused by invoking [start].
 */
class AsyncIPNetwork : IPNetwork, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private lateinit var job: Job

    private lateinit var forwardChannel: Channel<Any>
    private lateinit var callbackCounter : CountingLatch
    private val forwardMap = ConcurrentHashMap<IPAddress, Pair<IPSocket, Channel<Any>>>()
    private val subscribers = mutableListOf<(IPPacket) -> Unit>()

    override fun createSocket(ipAddress: IPAddress): IPSocket {
        return AsyncIPSocket(ipAddress, forwardChannel, this, callbackCounter)
    }

    override fun subscribe(handler: (IPPacket) -> Unit) {
        subscribers.add(handler)
    }

    /**
     * Starts the asynchronous tasks for packet reception and delivery
     */
    suspend fun start() {
        job = Job()
        callbackCounter = CountingLatch(1, 0, job)
        forwardChannel = Channel(Channel.UNLIMITED)
        launch {
            for (p in forwardChannel) {
                when (p) {
                    is AsyncIPSocket.ConnectSocketPacket -> {
                        forwardMap[p.socket.ipAddress] = Pair(p.socket, p.channel)
                    }
                    is AsyncIPSocket.CloseSocketPacket -> {
                        forwardMap[p.socket.ipAddress]?.second?.close()
                        forwardMap.remove(p.socket.ipAddress)
                    }
                    is IPPacket -> {
                        val receiverChannel = forwardMap[p.receiver]?.second
                        if (receiverChannel == null) {
                            forwardMap[p.sender]?.second?.send(AsyncIPSocket.PacketDeliveryFailedPacket(p))
                        } else {
                            subscribers.forEach { it(p) }
                            receiverChannel.send(p)
                        }
                    }
                }
            }
        }

    }

    /**
     * Stops the network and waits for all remaining packets to be delivered
     */
    suspend fun stop() {
        forwardMap.forEach { it.value.first.close() }
        forwardChannel.close()
        job.cancelAndJoin()
    }

    /**
     * Waits for all callbacks to finish
     */
    suspend fun waitForCallbacks() {
        callbackCounter.decrement()
        callbackCounter.wait()
        callbackCounter.reset()
    }
}
