package au.csiro.data61.pcnsimulation.protocol.channel

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Static information about a channel, i.e. all information that could be gathered from the funding transaction of a channel.
 */
data class StaticChannelInformation(
        val fromWallet: WalletAddress,
        val fromLiquidity: Double,
        val toWallet: WalletAddress,
        val toLiquidity: Double
) {
    val funding = fromLiquidity + toLiquidity

    fun toDynamicChannelInformation(sourceWallet: WalletAddress, fee: ChannelFee) : DynamicChannelInformation = when {
        fromWallet == sourceWallet -> {
            DynamicChannelInformation(fromWallet, toWallet, fromLiquidity, fee)
        }
        toWallet == sourceWallet -> {
            DynamicChannelInformation(toWallet, fromWallet, toLiquidity, fee)
        }
        else -> {
            throw Exception("SourceWallet $sourceWallet is not part of the channel.")
        }
    }

    fun liquidity(wallet: WalletAddress) : Double = if (wallet == fromWallet) fromLiquidity else toLiquidity

    fun otherWallet(walletAddress: WalletAddress): WalletAddress = if (walletAddress == fromWallet) toWallet else fromWallet

    fun hasWallet(wallet: WalletAddress) : Boolean = wallet == fromWallet || wallet == toWallet

    fun hasWallets(wallet1: WalletAddress, wallet2: WalletAddress) : Boolean = hasWallet(wallet1) && hasWallet(wallet2)
}
