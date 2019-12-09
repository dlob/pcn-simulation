package au.csiro.data61.pcnsimulation.protocol.message.request

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction

/**
 * Asks a peer to participate in a multi-channel transaction.
 *
 * Expects a [MultiChannelTransactionSecretResponse].
 */
data class MultiChannelTransactionRequest(
        val lockedTransaction: CommitmentTransaction,
        val hops: List<WalletAddress>,
        val hashLock: String,
        val unlock: (String) -> String
) : Request()
