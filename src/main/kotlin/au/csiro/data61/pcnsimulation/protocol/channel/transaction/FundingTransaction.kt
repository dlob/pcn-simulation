package au.csiro.data61.pcnsimulation.protocol.channel.transaction

import au.csiro.data61.pcnsimulation.WalletAddress
import java.security.PublicKey

/**
 * A [Transaction] that opens a new channel and specifies initial balances between the two participants.
 */
data class FundingTransaction(
        override val fromWallet: WalletAddress,
        override val toWallet: WalletAddress,
        override val inputs: List<Output>,
        override val outputs: List<Output>,
        override val fromSignature: String = "",
        override val toSignature: String = "",
        val fromPublicKey: PublicKey,
        val toPublicKey: PublicKey,
        override val cycle: Int = 0,
        override val counter: Int = 0,
        override val signature: String = "$counter"
) : Transaction