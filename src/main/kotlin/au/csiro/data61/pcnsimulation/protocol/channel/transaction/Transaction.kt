package au.csiro.data61.pcnsimulation.protocol.channel.transaction

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * A transaction is a basic exchange of agreements between two [WalletAddress]es. It is considered is agreed upon,
 * iff both signatures are provided.
 */
interface Transaction {
    val fromWallet: WalletAddress
    val toWallet: WalletAddress
    val inputs: List<Output>
    val outputs: List<Output>
    val fromSignature: String
    val toSignature: String
    val cycle: Int
    val counter: Int

    /**
     * Portion of the transaction that is signed. This would typically exclude [fromSignature] and [toSignature]
     */
    val signature: String

    /**
     * Returns the wallet of the respective other participant
     */
    fun otherWallet(walletAddress: WalletAddress): WalletAddress {
        return if (walletAddress == fromWallet) {
            toWallet
        } else {
            fromWallet
        }
    }

    /**
     * Returns the balance of the first participant
     */
    fun fromBalance() = balance(fromWallet)

    /**
     * Returns the balance of the second participant
     */
    fun toBalance() = balance(toWallet)

    /**
     * Returns the balance of the specified participant.
     */
    fun balance(walletAddress: WalletAddress): Double {
        val outputs = outputs.filter { it.recipient == walletAddress }
        val unconditional = outputs.filter { it is UnconditionalOutput }.sumByDouble { it.amount }
        val unlocked = outputs.filter { it is ConditionalOutput && it.isUnlocked() }.sumByDouble { it.amount }
        return unconditional + unlocked
    }

    /**
     * Returns the output that has the specified [hashLock], or `null` otherwise.
     */
    fun getByHashLock(hashLock: String): Output? {
        return outputs.find { it is ConditionalOutput && it.hashLock == hashLock }
    }
}