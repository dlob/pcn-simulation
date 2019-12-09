package au.csiro.data61.pcnsimulation.protocol.channel.transaction

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Specifies a recipient for the given amount. Implementations of output may define constraints and conditions for when
 * the amount can be claimed, i.e. outputs might be locked.
 */
interface Output {
    val recipient: WalletAddress
    val amount: Double
}