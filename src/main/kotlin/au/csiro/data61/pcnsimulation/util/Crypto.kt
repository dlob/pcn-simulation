package au.csiro.data61.pcnsimulation.util

import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import com.google.common.io.BaseEncoding
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utility functions
 */
object Crypto {
    private val initVector = "RandomInitVector"
    private val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    private val keyFactory = KeyFactory.getInstance("RSA")
    private val random = Random()

    init {
        keyPairGenerator.initialize(1024)
    }

    /**
     * Generates an 8-byte secret, encoded in Base64
     */
    fun generateSecret(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return BaseEncoding.base64().encode(bytes).substring(0..7)
    }

    /**
     * Generates a 16-byte symmetric key, encoded in Base64
     */
    fun generateSymmetricKey(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return BaseEncoding.base64().encode(bytes).substring(0..15)
    }

    /**
     * Generates a RSA1024 key pair
     */
    fun generateKeyPair(): KeyPair {
        return keyPairGenerator.genKeyPair()
    }

    /**
     * Encodes a public key
     */
    fun encodeKey(publicKey: PublicKey): String {
        val spec = keyFactory.getKeySpec(publicKey, X509EncodedKeySpec::class.java)
        return String(Base64.getEncoder().encode(spec.encoded))
    }

    /**
     * Encodes a private key
     */
    fun encodeKey(privateKey: PrivateKey): String {
        val spec = keyFactory.getKeySpec(privateKey, PKCS8EncodedKeySpec::class.java)
        return String(Base64.getEncoder().encode(spec.encoded))
    }

    /**
     * Decode a public key
     */
    fun decodePublicKey(publicKey: String): PublicKey {
        val spec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))
        return keyFactory.generatePublic(spec)
    }

    /**
     * Decode a private key
     */
    fun decodePrivateKey(privateKey: String): PrivateKey {
        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
        return keyFactory.generatePrivate(spec)
    }

    /**
     * Creates a wallet address, roughly following the Ethereum wallet specification
     * @param publicKey: public key of the owner of the wallet
     */
    fun createWallet(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return "0x" + BaseEncoding.base16().encode(digest.digest(publicKey.encoded)).substring(0..9)
    }

    /**
     * Returns the cryptographic signature of the given transaction according to its [Transaction.signature]
     *
     * @param transaction: data to be signed
     * @param privateKey: for providing authenticity
     */
    fun sign(transaction: Transaction, privateKey: PrivateKey) = sign(transaction.signature, privateKey)

    /**
     * Verifies the cryptographic signatures of both wallets with the according public keys
     *
     * @param transaction: transaction to be verified
     * @param fromPublicKey: public key of the sender
     * @param toPublicKey: public key of the recipient
     */
    fun verify(transaction: Transaction, fromPublicKey: PublicKey, toPublicKey: PublicKey): Boolean {
        return verify(transaction.signature, transaction.fromSignature, fromPublicKey)
                && verify(transaction.signature, transaction.toSignature, toPublicKey)
    }

    fun verify(channel: TransactionChannel): Pair<Boolean, String?> {
        val funding = channel.fundingTransaction
        val transactions = channel.transactions

        var signaturesValid = verify(funding, funding.fromPublicKey, funding.toPublicKey)
        var counterValid = funding.counter == 0
        var balancesValid = true

        for ((i, t) in transactions.withIndex()) {
            counterValid = counterValid && t.counter >= i + 1
            signaturesValid = signaturesValid && verify(t, funding.fromPublicKey, funding.toPublicKey)
            balancesValid = balancesValid && t.fromBalance() >= 0.0 && t.toBalance() >= 0.0
        }
        return if (!signaturesValid) {
            Pair(false, "Signatures are incorrect")
        } else if (!counterValid) {
            Pair(false, "Counter is incorrect")
        } else if (!balancesValid) {
            Pair(false, "Balances are incorrect")
        } else {
            Pair(true, null)
        }
    }

    /**
     * Returns the cryptographic signature of the given data with the private key
     *
     * @param plain: data to be signed
     * @param privateKey: for proving authenticity
     */
    fun sign(plain: String, privateKey: PrivateKey): String {
        val bytes = plain.toByteArray(StandardCharsets.UTF_8)
        val sig = Signature.getInstance("SHA256WithRSA")
        sig.initSign(privateKey)
        sig.update(bytes)
        val signatureBytes = sig.sign()
        return String(Base64.getEncoder().encode(signatureBytes))
    }

    /**
     * Verifies the private key signature with the according public key
     *
     * @param data: plain data to be verified
     * @param signature: cryptographic signature
     * @param publicKey: for proof of authenticity
     */
    fun verify(data: String, signature: String, publicKey: PublicKey): Boolean {
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        val signatureBytes = Base64.getDecoder().decode(signature)
        val sig = Signature.getInstance("SHA256WithRSA")
        sig.initVerify(publicKey)
        sig.update(bytes)
        return sig.verify(signatureBytes)
    }

    /**
     * Computes the SHA256 hash of an input
     *
     * @param plain: data to be encrypted
     */
    fun sha256(plain: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return BaseEncoding.base64().encode(digest.digest(plain.toByteArray(StandardCharsets.UTF_8)))
    }

    /**
     * Encrypts your data symmetrically with the given key
     *
     * @param plain: plain data to be encrypted
     * @param key: 16-bit key, can be obtained with [generateSymmetricKey]
     */
    fun encryptSymmetric(plain: String, key: String): String {
        val iv = IvParameterSpec(initVector.toByteArray(charset("UTF-8")))
        val keySpec = SecretKeySpec(key.toByteArray(charset("UTF-8")), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
        val encrypted = cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * Decrypts your symmetrically encrypted data
     *
     * @param encrypted: data to be decrypted
     * @param key: 16-bit key
     */
    fun decryptSymmetric(encrypted: String, key: String): String {
        val iv = IvParameterSpec(initVector.toByteArray(charset("UTF-8")))
        val keySpec = SecretKeySpec(key.toByteArray(charset("UTF-8")), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)
        val original = cipher.doFinal(Base64.getDecoder().decode(encrypted))
        return String(original)
    }
}
