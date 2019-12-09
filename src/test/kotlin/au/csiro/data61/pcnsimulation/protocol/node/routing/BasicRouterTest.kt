package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.SyncIPNetwork
import au.csiro.data61.pcnsimulation.protocol.node.BasicNode
import au.csiro.data61.pcnsimulation.configuration.UniformDistribution
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplateBuilder
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.FullDisclosureStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.NoFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.OnePercentFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.RelayAllPaymentsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.util.MockBlockchain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class BasicRouterTest {

    private lateinit var network: SyncIPNetwork
    private lateinit var node1: BasicNode
    private lateinit var node2: BasicNode
    private lateinit var node3: BasicNode
    private lateinit var node4: BasicNode
    private lateinit var nodes: List<BasicNode>

    @Before
    fun setup() {
        network = SyncIPNetwork()

        // Create node templates
        val nodeTemplates = NetworkTemplateBuilder()
                .size(4)
                .wealthDistribution(UniformDistribution(0.0, 0.0))
                .channelDistribution(UniformDistribution(0.0, 0.0))
                .channelFundingDistribution(UniformDistribution(0.0, 0.0))
                .build().nodes

        val channelStrategy = ApproveAllChannelsStrategy()
        val informationStrategy = FullDisclosureStrategy()
        val feeStrategy = NoFeeStrategy()
        val routingStrategy = CheapestRouteStrategy()
        val paymentRelayingStrategy = RelayAllPaymentsStrategy()

        // Create nodes
        val basicRouterPeers = ConcurrentHashMap<WalletAddress, Peer>()
        val basicRouterChannels = ConcurrentHashMap.newKeySet<StaticChannelInformation>()
        nodes = nodeTemplates.map {
            val socket = network.createSocket(it.ipAddress)
            val peer = Peer(it.name, it.keyPair.public, it.walletAddress, it.ipAddress)
            basicRouterPeers[it.walletAddress] = peer
            val strategy = Strategy()
            strategy.channel = channelStrategy
            strategy.information = informationStrategy
            strategy.fee = feeStrategy
            strategy.route = routingStrategy
            strategy.paymentRelaying = paymentRelayingStrategy
            val router = BasicRouter(peer, socket, strategy, basicRouterPeers, basicRouterChannels)
            BasicNode(it.name, it.keyPair, it.walletAddress,
                    MockBlockchain, strategy, emptySet(), socket, router)
        }

        // Assign nodes
        node1 = nodes.component1()
        node2 = nodes.component2()
        node3 = nodes.component3()
        node4 = nodes.component4()
        nodes.forEach { n -> n.subscribe { println(n.name + ": " + it.toString()) } }
        network.subscribe { println(it.toString()) }
    }

    @Test
    fun `Update Network Information and Check Node Propagation`() = runBlocking {
        node4.socket.close()
        assertFalse("Node4 shouldn't know about a channel between node1 and node2.", node4.router.knownChannels.any { it.hasWallets(node1.walletAddress, node2.walletAddress) })
        node1.openChannel(node2.walletAddress, 10.0, 10.0)

        node4.socket.connect()
        assertTrue("Node4 must know about a channel between node1 and node2.", node4.router.knownChannels.any { it.hasWallets(node1.walletAddress, node2.walletAddress) })
    }

    @Test
    fun `Make Multi-Channel Transaction with Two Options and Validate Routing Decision`() = runBlocking {

        /*
         *          node2 (1% fee)
         *        /      \
         * node 1         node4
         *       \      /
         *        node3 (0% fee)
         */

        node2.strategy.fee = OnePercentFeeStrategy()

        node1.openChannel(node2.walletAddress, 10.0, 10.0)
        node1.openChannel(node3.walletAddress, 10.0, 10.0)

        node2.openChannel(node4.walletAddress, 10.0, 10.0)
        node3.openChannel(node4.walletAddress, 10.0, 10.0)

        node1.makeMultiChannelTransaction(node4.walletAddress, 5.0)

        val channel12 = node1.channels.find { it.fromWallet == node1.walletAddress && it.toWallet == node2.walletAddress }!!
        val channel13 = node1.channels.find { it.fromWallet == node1.walletAddress && it.toWallet == node3.walletAddress }!!
        val channel24 = node2.channels.find { it.fromWallet == node2.walletAddress && it.toWallet == node4.walletAddress }!!
        val channel34 = node3.channels.find { it.fromWallet == node3.walletAddress && it.toWallet == node4.walletAddress }!!

        assertEquals(10.0, channel12.fromBalance(), 0.01)
        assertEquals(10.0, channel24.fromBalance(), 0.01)

        assertEquals(5.0, channel13.fromBalance(), 0.01)
        assertEquals(5.0, channel34.fromBalance(), 0.01)
    }

    @Test
    fun `Make Multi-Channel Transaction with Bidirectional Options and Validate Routing Decision`() = runBlocking {

        /*
         *          node2
         *        /  |   \
         * node 1    |    node4
         *       \   |  /
         *        node3
         */

        node1.openChannel(node2.walletAddress, 0.0, 10.0)
        node1.openChannel(node3.walletAddress, 10.0, 10.0)

        node2.openChannel(node3.walletAddress, 0.0, 10.0)
        node2.openChannel(node4.walletAddress, 10.0, 10.0)

        node3.openChannel(node4.walletAddress, 0.0, 10.0)

        node1.makeMultiChannelTransaction(node4.walletAddress, 5.0)

        val channel12 = node1.channels.find { it.fromWallet == node1.walletAddress && it.toWallet == node2.walletAddress }!!
        val channel13 = node1.channels.find { it.fromWallet == node1.walletAddress && it.toWallet == node3.walletAddress }!!
        val channel23 = node2.channels.find { it.fromWallet == node2.walletAddress && it.toWallet == node3.walletAddress }!!
        val channel24 = node2.channels.find { it.fromWallet == node2.walletAddress && it.toWallet == node4.walletAddress }!!
        val channel34 = node3.channels.find { it.fromWallet == node3.walletAddress && it.toWallet == node4.walletAddress }!!

        assertEquals(0.0, channel12.fromBalance(), 0.01)
        assertEquals(5.0, channel13.fromBalance(), 0.01)
        assertEquals(5.0, channel23.fromBalance(), 0.01)
        assertEquals(5.0, channel23.toBalance(), 0.01)
        assertEquals(5.0, channel24.fromBalance(), 0.01)
        assertEquals(15.0, channel24.toBalance(), 0.01)
        assertEquals(0.0, channel34.fromBalance(), 0.01)
        assertEquals(10.0, channel34.toBalance(), 0.01)
    }
}
