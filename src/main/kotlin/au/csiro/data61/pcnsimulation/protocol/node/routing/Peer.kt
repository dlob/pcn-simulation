package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.WalletAddress
import java.security.PublicKey

/**
 * Publicly available information about a node.
 */
data class Peer(
        val name: String,
        val publicKey: PublicKey,
        val walletAddress: WalletAddress,
        val ipAddress: IPAddress
)
