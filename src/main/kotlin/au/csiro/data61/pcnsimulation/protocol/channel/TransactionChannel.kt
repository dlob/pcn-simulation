package au.csiro.data61.pcnsimulation.protocol.channel

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.*
import au.csiro.data61.pcnsimulation.util.Crypto
import java.security.PrivateKey

/**
 * Bounded transaction channel between two information
 */
data class TransactionChannel(
        val fundingTransaction: FundingTransaction,
        val transactions: MutableList<CommitmentTransaction> = mutableListOf(),
        val fromWallet: WalletAddress = fundingTransaction.fromWallet,
        val toWallet: WalletAddress = fundingTransaction.toWallet
) {

    fun otherWallet(walletAddress: WalletAddress) = getLatest().otherWallet(walletAddress)

    fun claim(hashLock: String, secret: String): Boolean {
        val newTransaction = transactions.last().claim(hashLock, secret)
        return if (newTransaction != null) {
            transactions.add(newTransaction)
            true
        } else {
            false
        }
    }

    fun rollback(hashLock: String) {
        val newTransaction = transactions.last().rollback(hashLock)
        transactions.add(newTransaction)
    }

    /**
     * Creates a new [CommitmentTransaction] with the given properties. The new transaction is not added to the channel.
     */
    fun createLockedTransaction(sender: WalletAddress, amount: Double, hashLock: String, unlock: (String) -> String, privateKey: PrivateKey, cycle: Int): CommitmentTransaction? {
        val receiver = otherWallet(sender)
        val previous = getLatest()
        if (previous.balance(sender) >= amount) {
            val senderOutput = previous.outputs.find { it is UnconditionalOutput && it.recipient == sender } as UnconditionalOutput
            val newSenderOutput = senderOutput.copy(amount = senderOutput.amount - amount)
            val newReceiverOutput = ConditionalOutput(
                    recipient = receiver,
                    amount = amount,
                    hashLock = hashLock,
                    unlock = unlock
            )
            val newOutputs = previous.outputs - senderOutput + newSenderOutput + newReceiverOutput
            val t = CommitmentTransaction(
                    previous.fromWallet,
                    previous.toWallet,
                    previous.outputs,
                    newOutputs,
                    cycle,
                    counter() + 1
            )
            return if (t.fromWallet == sender) {
                t.copy(fromSignature = Crypto.sign(t, privateKey))
            } else {
                t.copy(toSignature = Crypto.sign(t, privateKey))
            }
        } else {
            return null
        }
    }

    /**
     * Creates a new [CommitmentTransaction] with the given properties. The new transaction is not added to the channel.
     *
     * @return a new commitment transaction with the specified output, if sufficient funds are available; null else.
     */
    fun createCommitmentTransaction(sender: WalletAddress, amount: Double, privateKey: PrivateKey, cycle: Int): CommitmentTransaction? {

        val receiver = otherWallet(sender)
        val previous = getLatest()
        if (previous.balance(sender) >= amount) {
            val senderOutput = previous.outputs.find { it is UnconditionalOutput && it.recipient == sender } as UnconditionalOutput
            val receiverOutput = previous.outputs.find { it is UnconditionalOutput && it.recipient == receiver } as UnconditionalOutput
            val newSenderOutput = senderOutput.copy(amount = senderOutput.amount - amount)
            val newReceiverOutput = receiverOutput.copy(amount = receiverOutput.amount + amount)
            val newOutputs = previous.outputs - senderOutput - receiverOutput + newSenderOutput + newReceiverOutput
            val t = CommitmentTransaction(
                    previous.fromWallet,
                    previous.toWallet,
                    previous.outputs,
                    newOutputs,
                    cycle,
                    counter() + 1
            )
            return if (sender == t.fromWallet) {
                t.copy(fromSignature = Crypto.sign(t, privateKey))
            } else {
                t.copy(toSignature = Crypto.sign(t, privateKey))
            }
        } else {
            return null
        }
    }

    /**
     * Returns the balance from [fromWallet]
     */
    fun fromBalance(): Double = getLatest().balance(fromWallet)

    /**
     * Returns the balance from [toWallet]
     */
    fun toBalance(): Double = getLatest().balance(toWallet)

    /**
     * Get current transaction counter
     */
    fun counter() = transactions.lastOrNull()?.counter ?: 0

    fun balance(walletAddress: WalletAddress): Double {
        return when (walletAddress) {
            fundingTransaction.fromWallet -> fromBalance()
            fundingTransaction.toWallet -> toBalance()
            else -> throw IllegalArgumentException("No channel partner has the specified wallet address")
        }
    }

    fun getLatest(): Transaction {
        return if (transactions.isNotEmpty()) {
            transactions.last()
        } else {
            fundingTransaction
        }
    }

    override fun hashCode(): Int {
        return fromWallet.hashCode() + toWallet.hashCode() + fromBalance().toInt() + toBalance().toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionChannel

        if (fundingTransaction != other.fundingTransaction) return false
        return true
    }
}