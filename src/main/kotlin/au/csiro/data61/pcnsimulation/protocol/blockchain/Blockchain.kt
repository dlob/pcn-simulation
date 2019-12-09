package au.csiro.data61.pcnsimulation.protocol.blockchain

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import au.csiro.data61.pcnsimulation.template.SimulationTemplate

/**
 * Ground truth for all [WalletAddress]es and their balances
 */
interface Blockchain {

    /**
     * Current transaction fee
     */
    var fee: Double

    /**
     * Register a wallet with the specified balance on the blockchain.
     */
    fun registerWallet(walletAddress: WalletAddress, initialFunds: Double = 0.0)

    /**
     * Transfer the given amount from wallet [from] to wallet [to], if the balance of [from] is sufficient.
     *
     * @throws BlockchainException, if the funds for transferring the specified amount are insufficient
     */
    fun transfer(from: WalletAddress, to: WalletAddress, amount: Double)

    /**
     * Return the current balance of [walletAddress]
     */
    fun balance(walletAddress: WalletAddress): Double?

    /**
     * Open a new channel based on the provided funding transaction and lock the funds into the channel.
     *
     * @throws BlockchainException, if the funds for creating the channel are insufficient
     */
    fun openChannel(fundingTransaction: FundingTransaction)

    /**
     * Close an existing channel and return the channel funding back into the wallet balances,
     * based on the split specified by the transaction.
     *
     * @throws BlockchainException, if the funds for paying fees are insufficient
     */
    fun closeChannel(transaction: Transaction)

    /**
     * Reset all balances and channels.
     */
    fun reset()

    /**
     * Reset blockchain and initialize with balances specified in [template]
     */
    fun reset(template: SimulationTemplate)

    /**
     * Initialize blockchain with balnaces specified in [template] without reseting the previous state.
     */
    fun addState(template: SimulationTemplate)
}