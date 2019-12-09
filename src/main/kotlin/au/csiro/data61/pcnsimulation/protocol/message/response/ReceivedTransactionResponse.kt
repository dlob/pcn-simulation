package au.csiro.data61.pcnsimulation.protocol.message.response

import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction

/**
 * [Response] to a [TransactionRequest], confirming that an unlocked [CommitmentTransaction] was received.
 */
data class ReceivedTransactionResponse(
        val transaction: CommitmentTransaction
) : Response()
