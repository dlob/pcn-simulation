package au.csiro.data61.pcnsimulation.behavior.strategy.paymentRelaying

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.strategy.ApprovalResult
import au.csiro.data61.pcnsimulation.protocol.strategy.PaymentRelayingStrategy

class RelayAllPaymentsStrategy : PaymentRelayingStrategy {
    override fun accept(incomingWallet: WalletAddress, outgoingWallet: WalletAddress): ApprovalResult {
        return ApprovalResult.APPROVE
    }
}
