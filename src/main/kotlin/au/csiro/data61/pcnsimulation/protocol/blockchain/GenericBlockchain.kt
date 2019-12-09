package au.csiro.data61.pcnsimulation.protocol.blockchain

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.template.SimulationTemplate
import au.csiro.data61.pcnsimulation.util.Crypto
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

/**
 * Ground truth of the balance of [WalletAddress]es, as well as open channels.
 */
class GenericBlockchain(
        override var fee: Double = 0.001
) : Blockchain {
    companion object {
        val log by logger()
    }

    private val balances = mutableMapOf<WalletAddress, Double>()
    private val channels = mutableListOf<TransactionChannel>()
    private val mutex = Mutex()

    override fun registerWallet(walletAddress: WalletAddress, initialFunds: Double) {
        balances[walletAddress] = initialFunds
    }

    override fun transfer(from: WalletAddress, to: WalletAddress, amount: Double) {
        if (balances.containsKey(from) && balances.containsKey(to) && balances[from]!! >= amount) {
            val balanceFrom = balances[from]!!
            val balanceTo = balances[to]!!
            balances[from] = balanceFrom - amount - fee
            balances[to] = balanceTo + amount
        } else {
            throw BlockchainException("Insufficient funds for in $from for transferring $amount to $to")
        }
    }

    override fun balance(walletAddress: WalletAddress): Double? {
        return balances[walletAddress]
    }

    override fun openChannel(fundingTransaction: FundingTransaction) {
        runBlocking {
            mutex.lock(this)
            if (balance(fundingTransaction.fromWallet) == null) {
                throw BlockchainException("The address ${fundingTransaction.fromWallet} is not registered in this blockchain.")
            } else if (balance(fundingTransaction.toWallet) == null) {
                throw BlockchainException("The address ${fundingTransaction.toWallet} is not registered in this blockchain.")
            } else if (balance(fundingTransaction.fromWallet) ?: 0.0 < fundingTransaction.fromBalance() + fee / 2
                    || balance(fundingTransaction.toWallet) ?: 0.0 < fundingTransaction.toBalance() + fee / 2) {
                fundingTransaction.let { f ->
                    {
                        log.warn("Required: ${f.fromBalance() + fee / 2}, Actual: ${balance(f.fromWallet)}")
                        log.warn("Required: ${f.toBalance() + fee / 2}, Actual: ${balance(f.toWallet)}")
                    }
                }
                throw BlockchainException("Insufficient funds for opening the channel")
            }
            mutex.unlock(this)

            val channel = TransactionChannel(fundingTransaction = fundingTransaction, transactions = mutableListOf())
            if (Crypto.verify(channel).first) {
                balances[fundingTransaction.fromWallet] = balances[fundingTransaction.fromWallet]!! - (fundingTransaction.fromBalance() + fee / 2)
                balances[fundingTransaction.toWallet] = balances[fundingTransaction.toWallet]!! - (fundingTransaction.toBalance() + fee / 2)
                channels.add(channel)
            } else {
                throw BlockchainException("The committed transaction is invalid.")
            }
        }
    }

    override fun closeChannel(transaction: Transaction) {
        runBlocking {
            mutex.lock(this)
            if (transaction.fromBalance() + (balance(transaction.fromWallet) ?: 0.0) < fee / 2
                    || transaction.toBalance() + (balance(transaction.toWallet) ?: 0.0) < fee / 2) {
                throw BlockchainException("Insufficient funds for closing the channel")
            }
            val channel = findChannelByWallets(transaction.fromWallet, transaction.toWallet)
            if (channel != null) {
                if (transaction is CommitmentTransaction) {
                    channel.transactions.add(transaction)
                }
                if (Crypto.verify(channel).first) {
                    balances[transaction.fromWallet] = balances[transaction.fromWallet]!! + transaction.fromBalance() - fee / 2
                    balances[transaction.toWallet] = balances[transaction.toWallet]!! + transaction.toBalance() - fee / 2
                    channels.remove(channel)
                } else {
                    throw BlockchainException("The committed transaction is invalid.")
                }
            }
            mutex.unlock(this)
        }
    }

    override fun reset() {
        balances.clear()
        channels.clear()
    }

    override fun reset(template: SimulationTemplate) {
        reset()
        addState(template)
    }

    override fun addState(template: SimulationTemplate) {
        val nodesMap = template.network.nodes.map { Pair(it.walletAddress, it) }.toMap()
        for (node in template.network.nodes) {
            balances[node.walletAddress] = node.walletBalance
        }

        template.network.channels
                .forEach {
                    var fundingTransaction = FundingTransaction(
                            it.fromWallet,
                            it.toWallet,
                            emptyList(),
                            listOf(UnconditionalOutput(it.fromWallet, it.fromBalance), UnconditionalOutput(it.toWallet, it.toBalance)),
                            fromPublicKey = nodesMap.getValue(it.fromWallet).keyPair.public,
                            toPublicKey = nodesMap.getValue(it.toWallet).keyPair.public,
                            cycle = 0
                    )
                    fundingTransaction = fundingTransaction.copy(fromSignature = Crypto.sign(fundingTransaction, nodesMap.getValue(it.fromWallet).keyPair.private))
                    fundingTransaction = fundingTransaction.copy(toSignature = Crypto.sign(fundingTransaction, nodesMap.getValue(it.toWallet).keyPair.private))
                    balances[it.fromWallet] = balances[it.fromWallet]!! + it.fromBalance + fee / 2
                    balances[it.toWallet] = balances[it.toWallet]!! + it.toBalance + fee / 2
                    openChannel(fundingTransaction)
                }
    }

    private fun findChannelByWallets(aWallet: WalletAddress, bWallet: WalletAddress): TransactionChannel? {
        return channels.find {
            it.fundingTransaction.fromWallet == aWallet
                    && it.fundingTransaction.toWallet == bWallet
                    || it.fundingTransaction.fromWallet == bWallet
                    && it.fundingTransaction.toWallet == aWallet
        }
    }
}