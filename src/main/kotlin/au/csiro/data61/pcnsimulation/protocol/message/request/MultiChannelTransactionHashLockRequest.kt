package au.csiro.data61.pcnsimulation.protocol.message.request

data class MultiChannelTransactionHashLockRequest(
        val amount: Double
) : Request()
