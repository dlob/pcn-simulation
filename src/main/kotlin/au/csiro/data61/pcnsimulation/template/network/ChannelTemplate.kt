package au.csiro.data61.pcnsimulation.template.network

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Initial state of a channel.
 */
data class ChannelTemplate(
        /**
         * The source wallet of the channel (initiator).
         */
        val fromWallet: WalletAddress,

        /**
         * The destination wallet of the channel.
         */
        val toWallet: WalletAddress,

        /**
         * The locked up funding of the source wallet.
         */
        val fromBalance: Double,

        /**
         * The locked up funding of the destination wallet.
         */
        val toBalance: Double
)
