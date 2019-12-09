package au.csiro.data61.pcnsimulation.protocol.blockchain

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.template.SimulationTemplateBuilder
import au.csiro.data61.pcnsimulation.template.network.NodeTemplate
import au.csiro.data61.pcnsimulation.util.Crypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class GenericBlockchainTest {

    private lateinit var node1: NodeTemplate
    private lateinit var node2: NodeTemplate
    private lateinit var node3: NodeTemplate

    private val blockchain = GenericBlockchain()

    @Before
    fun setup() {
        val nodes = SimulationTemplateBuilder().tiny().build().network.nodes.toList()
        node1 = nodes.component1()
        node2 = nodes.component2()
        node3 = nodes.component3()
    }

    @Test
    fun `Check Newly Registered Wallet Balance`() {
        blockchain.registerWallet(node1.walletAddress)
        assertEquals(0.0, blockchain.balance(node1.walletAddress)!!, 0.01)
    }

    @Test
    fun `Transfer Tokens From Coinbase and Check Balances`() {
        blockchain.registerWallet(node1.walletAddress, 10.0)
        assertEquals(10.0, blockchain.balance(node1.walletAddress)!!, 0.01)
    }

    @Test
    fun `Reset Blockchain and Check Balance is Null`() {
        blockchain.registerWallet(node1.walletAddress, 10.0)
        blockchain.reset()
        assertNull(blockchain.balance(node1.walletAddress))
    }

    @Test
    fun `Transfer With Insufficient Funds`() {
        blockchain.registerWallet(node1.walletAddress)
        blockchain.registerWallet(node2.walletAddress)
        try {
            blockchain.transfer(node1.walletAddress, node2.walletAddress, 10.0)
            fail()
        } catch (e: BlockchainException) {
            assertEquals(0.0, blockchain.balance(node1.walletAddress)!!, 0.01)
            assertEquals(0.0, blockchain.balance(node2.walletAddress)!!, 0.01)
        }
    }

    @Test
    fun `Open Channel and Check Funds Are Locked`() = runBlocking {
        blockchain.fee = 0.0
        blockchain.registerWallet(node1.walletAddress, 10.0)
        blockchain.registerWallet(node2.walletAddress, 10.0)

        var fundingTransaction = FundingTransaction(
                node1.walletAddress,
                node2.walletAddress,
                emptyList(),
                listOf(UnconditionalOutput(node1.walletAddress, 10.0), UnconditionalOutput(node2.walletAddress, 10.0)),
                fromPublicKey = node1.keyPair.public,
                toPublicKey = node2.keyPair.public
        )
        fundingTransaction = fundingTransaction.copy(fromSignature = Crypto.sign(fundingTransaction, node1.keyPair.private))
        fundingTransaction = fundingTransaction.copy(toSignature = Crypto.sign(fundingTransaction, node2.keyPair.private))

        blockchain.openChannel(fundingTransaction)
        assertEquals(0.0, blockchain.balance(node1.walletAddress)!!, 0.01)
        assertEquals(0.0, blockchain.balance(node2.walletAddress)!!, 0.01)
    }

    @Test
    fun `Open Channel With Insufficient Funds`() = runBlocking {
        blockchain.registerWallet(node1.walletAddress, 10.0)
        blockchain.registerWallet(node2.walletAddress, 10.0)

        val fundingTransaction = FundingTransaction(
                node1.walletAddress,
                node2.walletAddress,
                emptyList(),
                listOf(UnconditionalOutput(node1.walletAddress, 10.0), UnconditionalOutput(node2.walletAddress, 20.0)),
                fromPublicKey = node1.keyPair.public,
                toPublicKey = node2.keyPair.public
        )

        try {
            blockchain.openChannel(fundingTransaction)
            fail()
        } catch (e: BlockchainException) {
            assertEquals(10.0, blockchain.balance(node1.walletAddress)!!, 0.01)
            assertEquals(10.0, blockchain.balance(node2.walletAddress)!!, 0.01)
        }
    }

    @Test
    fun `Close Channel and Check Funds Update`() = runBlocking {
        blockchain.fee = 0.0
        blockchain.registerWallet(node1.walletAddress, 10.0)
        blockchain.registerWallet(node2.walletAddress, 20.0)

        var fundingTransaction = FundingTransaction(
                node1.walletAddress,
                node2.walletAddress,
                emptyList(),
                listOf(UnconditionalOutput(node1.walletAddress, 10.0), UnconditionalOutput(node2.walletAddress, 20.0)),
                fromPublicKey = node1.keyPair.public,
                toPublicKey = node2.keyPair.public
        )
        fundingTransaction = fundingTransaction.copy(fromSignature = Crypto.sign(fundingTransaction, node1.keyPair.private))
        fundingTransaction = fundingTransaction.copy(toSignature = Crypto.sign(fundingTransaction, node2.keyPair.private))

        var commitmentTransaction = CommitmentTransaction(
                node1.walletAddress,
                node2.walletAddress,
                fundingTransaction.outputs,
                listOf(UnconditionalOutput(node1.walletAddress, 5.0), UnconditionalOutput(node2.walletAddress, 25.0)),
                0,
                1
        )
        commitmentTransaction = commitmentTransaction.copy(fromSignature = Crypto.sign(commitmentTransaction, node1.keyPair.private))
        commitmentTransaction = commitmentTransaction.copy(toSignature = Crypto.sign(commitmentTransaction, node2.keyPair.private))

        blockchain.openChannel(fundingTransaction)
        blockchain.closeChannel(commitmentTransaction)

        assertEquals(5.0, blockchain.balance(node1.walletAddress)!!, 0.01)
        assertEquals(25.0, blockchain.balance(node2.walletAddress)!!, 0.01)
    }
}
