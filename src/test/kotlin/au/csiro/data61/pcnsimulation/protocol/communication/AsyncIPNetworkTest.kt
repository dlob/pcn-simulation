package au.csiro.data61.pcnsimulation.protocol.communication

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AsyncIPNetworkTest {

    @Test
    fun `Create AsyncIPSocket`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket = network.createSocket("127.0.0.1")
        assertEquals(AsyncIPSocket::class.java.name, socket.javaClass.name)
        assertEquals("127.0.0.1", socket.ipAddress)
        network.stop()
    }

    @Test
    fun `Communication between sockets invoke callbacks`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        var networkExecuted = false
        network.subscribe {
            networkExecuted = true
        }

        val socket1 = network.createSocket("10.0.0.1")
        socket1.connect()
        var socket1Executed = false
        socket1.registerReceiveCallback {
            socket1Executed = true
        }

        val socket2 = network.createSocket("10.0.0.2")
        socket2.connect()
        var socket2Executed = false
        socket2.registerReceiveCallback {
            socket2Executed = true
        }

        val socket3 = network.createSocket("10.0.0.3")
        socket3.connect()
        var socket3Executed = false
        socket3.registerReceiveCallback {
            socket3Executed = true
        }

        socket1.notify("10.0.0.2", true)

        network.waitForCallbacks()
        network.stop()
        assertTrue(networkExecuted)
        assertFalse(socket1Executed)
        assertTrue(socket2Executed)
        assertFalse(socket3Executed)
    }

    @Test
    fun `Socket can be connected and disconnected from the network`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket = network.createSocket("10.0.0.1")
        assertFalse(socket.isConnected)
        socket.connect()
        assertTrue(socket.isConnected)
        socket.close()
        assertFalse(socket.isConnected)
        network.stop()
    }

    @Test
    fun `Closed socket throws exception on sending`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket = network.createSocket("10.0.0.1")
        try {
            socket.notify("10.0.0.2", 42)
            assertFalse("Should not be executed.", true)
        } catch (ex: SocketClosedException) {
            assertTrue("SocketClosedException is raised.", true)
        }
        try {
            socket.request("10.0.0.2", 42)
            assertFalse("Should not be executed.", true)
        } catch (ex: SocketClosedException) {
            assertTrue("SocketClosedException is raised.", true)
        }
        try {
            socket.request("10.0.0.2", 42, 100)
            assertFalse("Should not be executed.", true)
        } catch (ex: SocketClosedException) {
            assertTrue("SocketClosedException is raised.", true)
        }
        try {
            socket.respond("10.0.0.2", "12345678", 42)
            assertFalse("Should not be executed.", true)
        } catch (ex: SocketClosedException) {
            assertTrue("SocketClosedException is raised.", true)
        }
        network.stop()
    }

    @Test
    fun `Network rejects undeliverable packets`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        var networkExecuted = false
        network.subscribe {
            networkExecuted = true
        }
        val socket = network.createSocket("10.0.0.1")
        socket.connect()
        socket.notify("10.0.0.2", true)
        network.stop()
        assertFalse(networkExecuted)
    }

    @Test
    fun `Waiting for callbacks to finish`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket1 = network.createSocket("10.0.0.1")
        socket1.connect()
        for (i in 2..99) {
            val socket = network.createSocket("10.0.0.$i")
            socket.connect()
            socket.registerReceiveCallback {
                assertEquals("10.0.0.${i - 1}", it.sender)
                assertEquals("10.0.0.$i", it.receiver)
                socket.notify("10.0.0.${i + 1}", true)
            }
        }
        val socket100 = network.createSocket("10.0.0.100")
        socket100.connect()
        var executed = false
        socket100.registerReceiveCallback {
            executed = true
        }

        socket1.notify("10.0.0.2", true)
        network.waitForCallbacks()

        assertTrue(executed)

        network.stop()
    }

    @Test
    fun `Request waits for response`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket1 = network.createSocket("10.0.0.1")
        socket1.connect()
        val socket2 = network.createSocket("10.0.0.2")
        socket2.connect()
        socket2.registerReceiveCallback {
            val payload = it.payload
            when(payload) {
                is Int -> {
                    // increment and echo
                    socket2.respond(it.sender, it.id, payload + 1)
                }
                else -> {
                    assertFalse("Payload must be of type Int.", true)
                }
            }
        }

        val response = socket1.request("10.0.0.2", 42) as Int
        assertEquals(43, response)

        network.stop()
    }

    @Test
    fun `Timeout fires on unanswered request`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket1 = network.createSocket("10.0.0.1")
        socket1.connect()
        val socket2 = network.createSocket("10.0.0.2")
        socket2.connect()

        try {
            socket1.request("10.0.0.2", 42, 100)
            assertFalse("Should not be executed.", true)
        } catch (ex: RequestUnansweredException) {
            assertTrue("RequestUnansweredException is raised.", true)
        }
        network.waitForCallbacks() // ensure active callbacks are calculated properly even on timeout
        network.stop()
    }

    @Test
    fun `Unexpected response is ignored`() = runBlocking {
        val network = AsyncIPNetwork()
        network.start()
        val socket1 = network.createSocket("10.0.0.1")
        socket1.connect()
        val socket2 = network.createSocket("10.0.0.2")
        socket2.connect()
        var socket2Executed = false
        socket2.registerReceiveCallback {
            socket2Executed = true
        }
        socket1.respond("10.0.0.2", "12345678",42)
        network.stop()
        assertFalse(socket2Executed)
    }
}
