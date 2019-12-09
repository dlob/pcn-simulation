package au.csiro.data61.pcnsimulation.protocol.channel.transaction

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Output that can be claimed immediately.
 */
data class UnconditionalOutput(
        override val recipient: WalletAddress,
        override val amount: Double
) : Output