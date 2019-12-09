package au.csiro.data61.pcnsimulation.protocol.channel

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.ConditionalOutput
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.util.Crypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionChannelTest {

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
    private val channel = TransactionChannel(funding)

    @Test
    fun `Create Channel and Check Basic Properties`() {

        assertEquals(10.0, channel.fromBalance(), 0.01)
        assertEquals(5.0, channel.toBalance(), 0.01)
        assertEquals(10.0, channel.balance("a"), 0.01)
        assertEquals(5.0, channel.balance("b"), 0.01)
        assertEquals("b", channel.otherWallet("a"))
        assertEquals("a", channel.otherWallet("b"))
        assertEquals(0, channel.counter())
        assertEquals(funding, channel.getLatest())
    }

    @Test
    fun `Add Transaction and Check Balance Update`() {

        val t = channel.createCommitmentTransaction("a", 1.0, Crypto.generateKeyPair().private, 0)!!

        assertTrue(t.verify())
        assertEquals(2, t.outputs.size)

        channel.transactions.add(t)

        assertEquals(t, channel.getLatest())
        assertEquals(1, channel.counter())
        assertEquals(9.0, channel.fromBalance(), 0.01)
        assertEquals(6.0, channel.toBalance(), 0.01)
    }

    @Test
    fun `Add Locked Transaction and Check Balance Update`() {

        val t = channel.createLockedTransaction("a", 1.0, "unlock", { x -> x }, Crypto.generateKeyPair().private, 0)!!

        assertEquals(1.0, t.outputs.find { it is ConditionalOutput && it.hashLock == "unlock" }?.amount)

        channel.transactions.add(t)

        assertEquals(t, channel.getLatest())
        assertEquals(1, channel.counter())
        assertEquals(9.0, channel.fromBalance(), 0.01)
        assertEquals(5.0, channel.toBalance(), 0.01)
    }

    @Test
    fun `Unlock Locked Transaction and Check Balance Update`() {

        val t = channel.createLockedTransaction("a", 1.0, "unlock", { x -> x }, Crypto.generateKeyPair().private, 0)!!
        channel.transactions.add(t)

        assertEquals(9.0, channel.fromBalance(), 0.01)
        assertEquals(5.0, channel.toBalance(), 0.01)

        val claim = channel.claim("unlock", "unlock")

        assertTrue(claim)
        assertEquals(9.0, channel.fromBalance(), 0.01)
        assertEquals(6.0, channel.toBalance(), 0.01)
    }

    @Test
    fun `Add Multiple Locked Transaction and Unlock in Reverse Order`() {

        val t1 = channel.createLockedTransaction("a", 1.0, "unlock1", { x -> x }, Crypto.generateKeyPair().private, 0)!!
        channel.transactions.add(t1)
        val t2 = channel.createLockedTransaction("a", 1.0, "unlock2", { x -> x }, Crypto.generateKeyPair().private, 0)!!
        channel.transactions.add(t2)

        assertEquals(8.0, channel.fromBalance(), 0.01)
        assertEquals(5.0, channel.toBalance(), 0.01)

        channel.claim("unlock2", "unlock2")

        assertEquals(8.0, channel.fromBalance(), 0.01)
        assertEquals(6.0, channel.toBalance(), 0.01)

        channel.claim("unlock1", "unlock1")

        assertEquals(8.0, channel.fromBalance(), 0.01)
        assertEquals(7.0, channel.toBalance(), 0.01)
    }
}