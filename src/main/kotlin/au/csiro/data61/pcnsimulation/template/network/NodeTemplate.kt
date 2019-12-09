package au.csiro.data61.pcnsimulation.template.network

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.WalletAddress
import java.security.KeyPair

/**
 * Initial state of a node.
 */
data class NodeTemplate(
        /**
         * Name of the node.
         */
        val name: String,

        /**
         * Private and public key of the node to sign transactions.
         */
        val keyPair: KeyPair,

        /**
         * IP-address of the node to communicate with other nodes.
         */
        val ipAddress: IPAddress,

        /**
         * Wallet-address on the blockchain.
         */
        val walletAddress: WalletAddress,

        /**
         * Balance of the wallet.
         * The sum of all locked up funds in channels have to be lower or equal.
         */
        val walletBalance: Double
)
