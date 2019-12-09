package au.csiro.data61.pcnsimulation.protocol.channel.transaction

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * A [Transaction] that specifies all conditional and unconditional balances between two [WalletAddress]es.
 */
data class CommitmentTransaction(

        /**
         * Address that specifies the first participant in the channel.
         */
        override val fromWallet: WalletAddress,

        /**
         * Address that specifies the second participant in the channel.
         */
        override val toWallet: WalletAddress,

        /**
         * List of inputs, determining the previous balance
         */
        override val inputs: List<Output>,

        /**
         * List of outputs, determining the updated balance
         */
        override val outputs: List<Output>,

        /**
         * Indicates the cycle the transaction has been issued
         */
        override val cycle: Int,

        /**
         * Transaction counter. This counter increases for every transaction that is committed to a channel.
         */
        override val counter: Int,

        /**
         * Cryptographic signature of the first participant.
         */
        override val fromSignature: String = "",

        /**
         * Cryptographic signature of the second participant.
         */
        override val toSignature: String = "",

        /**
         * String of the transaction that is signed by the participants.
         */
        override val signature: String = "$fromWallet$toWallet$inputs$outputs$counter"

) : Transaction {

    /**
     * Returns a new commitment transaction with the unlocked output for a previously locked transaction, if successful;
     * null otherwise
     */
    fun claim(hashLock: String, secret: String): CommitmentTransaction? {
        val locked = outputs.find { it is ConditionalOutput && it.hashLock == hashLock }
        return if (locked != null) {
            locked as ConditionalOutput
            val unlocked = locked.copy(secret = secret)
            if (unlocked.isUnlocked()) {
                val newOutput = outputs - locked + unlocked
                this.copy(outputs = newOutput, counter = counter + 1)
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Returns a new commitment transaction with the previously locked output reversed
     */
    fun rollback(hashLock: String): CommitmentTransaction {
        val locked = outputs.find { it is ConditionalOutput && it.hashLock == hashLock }!!
        val unlocked = UnconditionalOutput(locked.recipient, locked.amount)
        val newOutput = outputs - locked + unlocked
        return this.copy(outputs = newOutput, counter = counter + 1)
    }

    /**
     * Verify that the amount specified in all inputs is equal to the amount specified in all outputs.
     */
    fun verify(): Boolean {
        return inputs.sumByDouble { it.amount } == outputs.sumByDouble { it.amount }
    }
}
