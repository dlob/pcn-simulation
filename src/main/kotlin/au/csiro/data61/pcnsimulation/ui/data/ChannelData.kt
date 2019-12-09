package au.csiro.data61.pcnsimulation.ui.data

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Data about a transaction channels offered by the API
 */
data class ChannelData(
        val fromWallet: WalletAddress,
        val toWallet: WalletAddress,
        val fromBalance: Double,
        val toBalance: Double
)