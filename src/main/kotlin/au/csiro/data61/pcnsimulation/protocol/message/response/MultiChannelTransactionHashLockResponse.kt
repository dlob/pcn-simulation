package au.csiro.data61.pcnsimulation.protocol.message.response

data class MultiChannelTransactionHashLockResponse(
        val hashLock: String,
        val unlock: (String) -> String
): Response()
