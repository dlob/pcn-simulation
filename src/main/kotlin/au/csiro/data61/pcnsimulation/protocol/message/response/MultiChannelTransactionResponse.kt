package au.csiro.data61.pcnsimulation.protocol.message.response

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction

data class MultiChannelTransactionResponse(
        val hashLock: String,
        val secret: String,
        val signedTransaction: CommitmentTransaction? = null,
        val failed: Boolean = false
): Response()
