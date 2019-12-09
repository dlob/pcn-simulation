package au.csiro.data61.pcnsimulation.protocol.channel

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.ConditionalOutput
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.util.Crypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTest {

    private val funding = FundingTransaction(
            "a",
            "b",
            emptyList(),
            listOf(UnconditionalOutput("a", 10.0), UnconditionalOutput("b", 5.0)),
            "sig-a",
            "sig-b",
            Crypto.generateKeyPair().public,
            Crypto.generateKeyPair().public
    )
    private val unlocked = CommitmentTransaction(
            "a",
            "b",
            funding.outputs,
            listOf(UnconditionalOutput("a", 9.0), UnconditionalOutput("b", 6.0)),
            0,
            1,
            "sig-a",
            "sig-b"
    )
    private val locked: CommitmentTransaction

    init {
        val c = ConditionalOutput("b", 1.0, "lock", unlock = { x -> x })
        val outputs = listOf(UnconditionalOutput("a", 9.0), UnconditionalOutput("b", 5.0), c)
        locked = unlocked.copy(outputs = outputs)
    }

    @Test
    fun `Create Funding Transaction and Check Basic Properties`() {
        assertEquals(10.0, funding.balance("a"), 0.01)
        assertEquals(10.0, funding.fromBalance(), 0.01)
        assertEquals(5.0, funding.balance("b"), 0.01)
        assertEquals(5.0, funding.toBalance(), 0.01)
        assertEquals("b", funding.otherWallet("a"))
        assertEquals("a", funding.otherWallet("b"))
    }

    @Test
    fun `Create Commitment Transaction and Check Basic Properties`() {
        assertEquals(9.0, unlocked.balance("a"), 0.01)
        assertEquals(9.0, unlocked.fromBalance(), 0.01)
        assertEquals(6.0, unlocked.balance("b"), 0.01)
        assertEquals(6.0, unlocked.toBalance(), 0.01)
        assertEquals("b", unlocked.otherWallet("a"))
        assertEquals("a", unlocked.otherWallet("b"))
        assertTrue(unlocked.verify())
    }

    @Test
    fun `Create Locked Commitment Transaction and Verify`() {
        assertTrue(locked.verify())
    }

    @Test
    fun `Create Locked Commitment Transaction and Check Balance`() {
        assertEquals(9.0, locked.fromBalance(), 0.01)
        assertEquals(5.0, locked.toBalance(), 0.01)
    }

    @Test
    fun `Claim Locked Commitment Transaction and Check Balance`() {
        val claimed = locked.claim("lock", "lock")
        assertEquals(9.0, claimed?.fromBalance())
        assertEquals(6.0, claimed?.toBalance())
    }
}