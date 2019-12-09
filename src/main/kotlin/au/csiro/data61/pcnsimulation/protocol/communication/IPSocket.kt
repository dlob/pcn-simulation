package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress

/**
 * Socket that connects a node to an [IPNetwork].
 * A socket is reusable after it has been closed. Registered callbacks are not cleared on close.
 */
interface IPSocket {
    /**
     * Address of the socket.
     */
    val ipAddress: IPAddress

    /**
     * Indicates that the socket is currently connected to the network.
     */
    val isConnected: Boolean

    /**
     * Send a notification to a [receiver].
     */
    suspend fun notify(receiver: IPAddress, payload: Any)

    /**
     * Send a request to a [receiver]. Returns the response.
     */
    suspend fun request(receiver: IPAddress, payload: Any) : Any

    /**
     * Send a request to a [receiver]. Returns the response within the given timeout or an exception is raised.
     */
    suspend fun request(receiver: IPAddress, payload: Any, timeoutMilliseconds: Long) : Any

    /**
     * Send a response to a [receiver].
     */
    suspend fun respond(receiver: IPAddress, requestId: String, payload: Any)

    /**
     * Register a callback function for receiving notifications and requests.
     */
    fun registerReceiveCallback(callback: suspend (IPPacket) -> Unit)

    /**
     * Connect the socket to the network and listen for packets.
     */
    suspend fun connect()

    /**
     * Close the connection to the network.
     */
    suspend fun close()
}
