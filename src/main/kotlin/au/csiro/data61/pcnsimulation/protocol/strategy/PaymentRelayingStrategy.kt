package au.csiro.data61.pcnsimulation.protocol.strategy

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Determines if relaying of a payment is accepted or declined.
 */
interface PaymentRelayingStrategy {
    fun accept(incomingWallet: WalletAddress, outgoingWallet: WalletAddress): ApprovalResult
}
