package au.csiro.data61.pcnsimulation.protocol.message.request

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction

/**
 * Informs a peer that a transaction has been issued on one of its channels.
 */
data class TransactionRequest(
        val transaction: CommitmentTransaction
) : Request()
