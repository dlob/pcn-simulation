package au.csiro.data61.pcnsimulation.protocol.channel

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Dynamic information about the current state of a transaction channel. This information is short-lived and needs to be
 * updated in short intervals or on-demand. It provides directed information about a channel from the view-point of
 * [fromWallet], i.e. [TransactionChannel.fromWallet] and [TransactionChannel.toWallet] may be interchanged for this information,
 * depending on the node that sends the information.
 *
 * Example: Nodes A (walletAddress "A") and B (walletAddress "B") open a channel:
 *
 * ```
 * FundingTransaction(
 *      fromWallet: "A",
 *      toWallet: "B",
 *      inputs: emptyList(),
 *      outputs: listOf(UnconditionalOutput("A", 10.0), UnconditionalOutput("B", 10.0))
 * )
 * ```
 *
 * For all transactions, `fromWallet` and `toWallet` will be ordered, i.e. `fromWallet` will always yield "A" while
 * `toWallet` will always yield "B". This also holds for messages that exchange [StaticChannelInformation]. However,
 * the ordering of `fromWallet` and `toWallet` in [DynamicChannelInformation] depends on the requested direction. If a
 * peer requests dynamic information from "A", `fromWallet` will contain "A". If, however, the request is sent to "B",
 * `fromWallet´ will contain "B".
 *
 *
 */
data class DynamicChannelInformation(
        val fromWallet: WalletAddress,
        val toWallet: WalletAddress,
        val liquidity: Double,
        val fee: ChannelFee
)
