package au.csiro.data61.pcnsimulation.environment

import au.csiro.data61.pcnsimulation.protocol.communication.SyncIPNetwork
import au.csiro.data61.pcnsimulation.configuration.RoutingAlgorithm
import au.csiro.data61.pcnsimulation.template.SimulationTemplate
import au.csiro.data61.pcnsimulation.template.network.ChannelTemplate
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplate
import au.csiro.data61.pcnsimulation.template.network.NodeTemplate
import au.csiro.data61.pcnsimulation.util.Crypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class SimulationBuilderTest {

    val aKey = Crypto.generateKeyPair()
    val bKey = Crypto.generateKeyPair()
    val cKey = Crypto.generateKeyPair()

    private val template = SimulationTemplate(
            emptySet(),
            NetworkTemplate(
                    setOf(
                            NodeTemplate("a", aKey, "0.0.0.1", "A", 1.0),
                            NodeTemplate("b", bKey, "0.0.0.2", "B", 1.0),
                            NodeTemplate("c", cKey, "0.0.0.3", "C", 1.0)
                    ),
                    setOf(
                            ChannelTemplate("A", "B", 1.0, 2.0),
                            ChannelTemplate("B", "C", 3.0, 4.0)
                    )
            ),
            emptyList(),
            RoutingAlgorithm.BASIC,
            0.0
    )

    private val network = SimulationBuilder().template(template).communication(SyncIPNetwork()).build().network

    @Test
    fun `Generate Network and Check Number of Nodes and Channels`() = runBlocking {
        Assert.assertEquals(3, network.nodes.size)
        Assert.assertEquals(2, network.channels.size)
    }

    @Test
    fun `Generate Network and Check Node Details`() = runBlocking {
        val node1 = network.nodes["A"]!!
        Assert.assertEquals("a", node1.name)
        Assert.assertEquals(aKey, node1.keyPair)
        Assert.assertEquals("A", node1.walletAddress)
        Assert.assertEquals("0.0.0.1", node1.socket.ipAddress)
        Assert.assertEquals(1, node1.channels.size)
    }

    @Test
    fun `Generate Network and Check Channel Details`() = runBlocking {
        val channel1 = network.channels.first()
        Assert.assertEquals("A", channel1.fromWallet)
        Assert.assertEquals("B", channel1.toWallet)
        Assert.assertEquals(1.0, channel1.fromBalance(), 0.01)
        Assert.assertEquals(2.0, channel1.toBalance(), 0.01)
    }
}
