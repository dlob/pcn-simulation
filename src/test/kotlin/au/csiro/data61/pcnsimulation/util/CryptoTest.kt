package au.csiro.data61.pcnsimulation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {

    @Test
    fun `Generate Symmetric Key and Check Length`() {
        assertEquals(16, Crypto.generateSymmetricKey().length)
    }

    @Test
    fun `Generate Secret and Check Length`() {
        assertEquals(8, Crypto.generateSecret().length)
    }

    @Test
    fun `Encrypt and Decrypt with Symmetric Key and Check Idempotence`() {
        val key = Crypto.generateSymmetricKey()
        assertEquals("Hello World", Crypto.decryptSymmetric(Crypto.encryptSymmetric("Hello World", key), key))
    }

    @Test
    fun `Generate Key Pair and Check Signatures`() {
        val keyPair = Crypto.generateKeyPair()
        val data = "Hello World"
        val signature = Crypto.sign(data, keyPair.private)
        assertTrue(Crypto.verify(data, signature, keyPair.public))
    }

    @Test
    fun `Create Wallet and Check Conformance`() {
        val keyPair = Crypto.generateKeyPair()
        val wallet = Crypto.createWallet(keyPair.public)
        assertEquals("0x", wallet.take(2))
        assertEquals(12, wallet.length)
    }
}