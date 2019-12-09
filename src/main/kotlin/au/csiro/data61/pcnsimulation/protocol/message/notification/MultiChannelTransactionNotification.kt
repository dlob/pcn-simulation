package au.csiro.data61.pcnsimulation.protocol.message.notification

/**
 * Informs the final recipient of a multi-channel transaction, that a transaction will be initiated.
 */
data class MultiChannelTransactionNotification(
        val hashLock: String
) : Notification()
