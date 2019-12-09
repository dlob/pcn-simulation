package au.csiro.data61.pcnsimulation.ui.data

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Data about nodes in a transaction channel network offered by the API
 */
data class NodeData(
        val name: String,
        val walletAddress: WalletAddress,
        val ipAddress: IPAddress,
        val balance: Double,
        val knownPeers: Set<WalletAddress>,
        val channels: Set<WalletAddress>,
        val lockedFunding: Double
)