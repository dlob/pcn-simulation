package au.csiro.data61.pcnsimulation.protocol.node

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.communication.SyncIPNetwork
import au.csiro.data61.pcnsimulation.protocol.message.request.PingRequest
import au.csiro.data61.pcnsimulation.protocol.message.response.PingResponse
import au.csiro.data61.pcnsimulation.protocol.node.routing.BasicRouter
import au.csiro.data61.pcnsimulation.protocol.node.routing.Peer
import au.csiro.data61.pcnsimulation.configuration.UniformDistribution
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplateBuilder
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import au.csiro.data61.pcnsimulation.behavior.strategy.channel.ApproveAllChannelsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.disclosure.FullDisclosureStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.NoFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.fees.OnePercentFeeStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying.RelayAllPaymentsStrategy
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.util.Crypto
import au.csiro.data61.pcnsimulation.util.MockBlockchain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class BasicNodeTest {

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
    }

    @Test
    fun `Ping and Check Online Status`() = runBlocking {
        val socket = network.createSocket("255.255.255.255")
        socket.connect()
        for (n in nodes) {
            val resp = socket.request(n.socket.ipAddress, PingRequest(), 100)
            assertTrue("Response is not a PingResponse.", resp is PingResponse)
            assertTrue("Socket of node ${n.name} is not connected to the network.", n.socket.isConnected)
        }
        socket.close()
    }

    @Test
    fun `Open and Close Channel`() = runBlocking {
        node1.openChannel(node2.walletAddress, 1.0, 1.0)
        assertEquals(1, node1.router.knownChannels.size)
        assertEquals(1, node2.router.knownChannels.size)
        node1.closeChannel(node2.walletAddress)
        assertEquals(0, node1.router.knownChannels.size)
        assertEquals(0, node2.router.knownChannels.size)
    }

    @Test
    fun `Request Channel and Check Channel Update`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)

        /*
         * Check that only one channel is created
         */
        assertEquals(1, node1.channels.size)
        assertEquals(1, node2.channels.size)

        /*
         * Check that basic funding transaction properties are correct
         */
        assertNotNull(node1.channels.map { it.fundingTransaction }.find {
            it.fromWallet == node1.walletAddress
                    && it.toWallet == node2.walletAddress
                    && it.fromBalance() == 10.0
                    && it.toBalance() == 20.0
                    && it.fromSignature != ""
                    && it.toSignature != ""
        })

        assertNotNull(node1.channels.map { it.fundingTransaction }.find {
            it.fromWallet == node1.walletAddress
                    && it.toWallet == node2.walletAddress
                    && it.fromBalance() == 10.0
                    && it.toBalance() == 20.0
                    && it.fromSignature != ""
                    && it.toSignature != ""
        })
    }

    @Test
    fun `Request Channel and Check Signatures`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)

        val funding1 = node1.channels.first().fundingTransaction
        val funding2 = node1.channels.first().fundingTransaction

        assertTrue(Crypto.verify(funding1, node1.keyPair.public, node2.keyPair.public))
        assertTrue(Crypto.verify(funding2, node1.keyPair.public, node2.keyPair.public))
    }

    @Test
    fun `Make Channel Transaction and Check Channel Update`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)
        node1.makeChannelTransaction(node2.walletAddress, 5.0)

        val channel1 = node1.channels.first()
        val channel2 = node2.channels.first()

        assertEquals(1, channel1.transactions.size)
        assertEquals(1, channel2.transactions.size)
    }

    @Test
    fun `Make Channel Transaction and Check Signatures`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)
        node1.makeChannelTransaction(node2.walletAddress, 5.0)

        val transaction1 = node1.channels.first().transactions.first()
        val transaction2 = node2.channels.first().transactions.first()

        assertTrue(Crypto.verify(transaction1, node1.keyPair.public, node2.keyPair.public))
        assertTrue(Crypto.verify(transaction2, node1.keyPair.public, node2.keyPair.public))
    }

    @Test
    fun `Make Channel Transactions and Check Counter`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)
        node1.makeChannelTransaction(node2.walletAddress, 5.0)
        node1.makeChannelTransaction(node2.walletAddress, 5.0)

        val transaction1 = node1.channels.first().transactions.component2()
        val transaction2 = node2.channels.first().transactions.component2()

        assertEquals(2, transaction1.counter)
        assertEquals(2, transaction2.counter)
    }

    @Test
    fun `Make Channel Transaction and Check Balances`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)

        val channel1 = node1.channels.first()

        assertEquals(10.0, channel1.fromBalance(), 0.01)
        assertEquals(20.0, channel1.toBalance(), 0.01)

        node1.makeChannelTransaction(node2.walletAddress, 5.0)

        assertEquals(5.0, channel1.fromBalance(), 0.01)
        assertEquals(25.0, channel1.toBalance(), 0.01)
    }

    @Test
    fun `Make Reverse Transaction and Check Balances`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)

        val channel1 = node1.channels.first()

        assertEquals(10.0, channel1.fromBalance(), 0.01)
        assertEquals(20.0, channel1.toBalance(), 0.01)

        node2.makeChannelTransaction(node1.walletAddress, 5.0)

        assertEquals(15.0, channel1.fromBalance(), 0.01)
        assertEquals(15.0, channel1.toBalance(), 0.01)
    }

    @Test
    fun `Make Transactions and Check Validity`() = runBlocking {
        node1.openChannel(node2.walletAddress, 10.0, 20.0)

        node1.makeChannelTransaction(node2.walletAddress, 5.0)
        node2.makeChannelTransaction(node1.walletAddress, 5.0)
        assertTrue(Crypto.verify(node1.channels.first()).first)
    }

    @Test
    fun `Close Channel and Check Closed Properly`() = runBlocking {

        node1.openChannel(node2.walletAddress, 10.0, 10.0)
        node1.makeChannelTransaction(node2.walletAddress, 5.0)
        node1.closeChannel(node2.walletAddress)

        assertEquals(0, node1.channels.size)
        assertEquals(0, node2.channels.size)
    }

    @Test
    fun `Make Multi-Channel Transaction and Check Balances`() = runBlocking {

        node1.openChannel(node2.walletAddress, 10.0, 10.0)
        node2.openChannel(node3.walletAddress, 10.0, 10.0)
        node1.makeMultiChannelTransaction(listOf(node2.walletAddress, node3.walletAddress), 5.0)

        val channelOneTwo = node1.channels.first()
        val channelTwoOne = node2.channels.find { it.fromWallet == node1.walletAddress && it.toWallet == node2.walletAddress }!!
        val channelTwoThree = node2.channels.find { it.fromWallet == node2.walletAddress && it.toWallet == node3.walletAddress }!!
        val channelThreeTwo = node3.channels.first()

        assertEquals(5.0, channelOneTwo.fromBalance(), 0.01)
        assertEquals(15.0, channelOneTwo.toBalance(), 0.01)

        assertEquals(5.0, channelTwoOne.fromBalance(), 0.01)
        assertEquals(15.0, channelTwoOne.toBalance(), 0.01)

        assertEquals(5.00, channelTwoThree.fromBalance(), 0.01)
        assertEquals(15.00, channelTwoThree.toBalance(), 0.01)

        assertEquals(5.00, channelThreeTwo.fromBalance(), 0.01)
        assertEquals(15.00, channelThreeTwo.toBalance(), 0.01)
    }

    @Test
    fun `Make Multi-Channel Transaction with Fees and Check Fees Are One Per Cent`() = runBlocking {

        node2.strategy.fee = OnePercentFeeStrategy()

        node1.openChannel(node2.walletAddress, 10.0, 10.0)
        node2.openChannel(node3.walletAddress, 10.0, 10.0)
        node1.makeMultiChannelTransaction(listOf(node2.walletAddress, node3.walletAddress), 5.0)

        val channelOneTwo = node1.channels.first()
        val channelTwoThree = node3.channels.first()

        assertEquals(4.95, channelOneTwo.fromBalance(), 0.01)
        assertEquals(15.05, channelOneTwo.toBalance(), 0.01)
        assertEquals(5.00, channelTwoThree.fromBalance(), 0.01)
        assertEquals(15.00, channelTwoThree.toBalance(), 0.01)
    }

    @Test
    fun `Make Multi-Channel Transaction with Automatic Routing and Check Route`() = runBlocking {

        node1.openChannel(node2.walletAddress, 10.0, 10.0)
        node2.openChannel(node3.walletAddress, 10.0, 10.0)
        val channel12 = node1.channels.first()
        val channel23 = node3.channels.first()

        assertEquals(10.0, channel12.fromBalance(), 0.01)
        assertEquals(10.0, channel12.toBalance(), 0.01)

        assertEquals(10.0, channel23.fromBalance(), 0.01)
        assertEquals(10.0, channel23.toBalance(), 0.01)

        node1.makeMultiChannelTransaction(node3.walletAddress, 5.0)

        assertEquals(5.0, channel12.fromBalance(), 0.01)
        assertEquals(15.0, channel12.toBalance(), 0.01)

        assertEquals(5.0, channel23.fromBalance(), 0.01)
        assertEquals(15.0, channel23.toBalance(), 0.01)
    }
}
