package au.csiro.data61.pcnsimulation.protocol.channel.transaction

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Output that can be claimed only if the correct secret was provided.
 */
data class ConditionalOutput(
        override val recipient: WalletAddress,
        override val amount: Double,
        val hashLock: String,
        val secret: String = "",
        val unlock: (String) -> String
) : Output {

    fun isUnlocked(): Boolean {
        return unlock(secret) == hashLock
    }
}