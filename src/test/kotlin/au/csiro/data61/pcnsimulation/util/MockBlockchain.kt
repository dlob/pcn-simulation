package au.csiro.data61.pcnsimulation.util

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.blockchain.Blockchain
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import au.csiro.data61.pcnsimulation.template.SimulationTemplate

object MockBlockchain : Blockchain {

    override var fee = 0.0

    override fun registerWallet(walletAddress: WalletAddress, initialFunds: Double) {}

    override fun transfer(from: WalletAddress, to: WalletAddress, amount: Double) {}

    override fun balance(walletAddress: WalletAddress) = Double.MAX_VALUE

    override fun openChannel(fundingTransaction: FundingTransaction) {}

    override fun closeChannel(transaction: Transaction) {}

    override fun reset() {}

    override fun reset(template: SimulationTemplate) {}

    override fun addState(template: SimulationTemplate) {}
}
