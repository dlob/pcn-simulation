package au.csiro.data61.pcnsimulation.protocol.node

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.agent.Agent
import au.csiro.data61.pcnsimulation.ui.data.Data
import au.csiro.data61.pcnsimulation.protocol.blockchain.Blockchain
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.node.routing.Router
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import java.security.KeyPair

/**
 * Offers protocol level interactions within a transaction channel network, including network discovery, single-channel
 * and multi-channel payments. Nodes may be used by [Agent]s to simulate dynamic behaviour of economic actors in
 * a network.
 */
interface Node {
    /**
     * Human-readable node alias
     */
    val name: String

    /**
     * Asymmetric key pair
     */
    val keyPair: KeyPair

    /**
     * The node's wallet address
     */
    val walletAddress: WalletAddress

    /**
     * The node's blockchain connection
     */
    val blockchain: Blockchain

    /**
     * The node's strategy, determining routing, fees and disclosure, etc.
     */
    val strategy: Strategy

    /**
     * All open channels the node is directly participating in
     */
    val channels: List<TransactionChannel>

    /**
     * The socket for accessing the communication network.
     */
    val socket: IPSocket

    /**
     * The router finds peers and paths in the network
     */
    val router: Router


    /**
     * (Re-)connect to the communication network
     */
    suspend fun goOnline()

    /**
     * Disconnect from the communication network
     */
    suspend fun goOffline()

    /**
     * Request to open a [TransactionChannel] with another peer
     */
    suspend fun openChannel(receiver: WalletAddress, aBound: Double, bBound: Double)

    /**
     * Requests to close a [TransactionChannel]
     */
    suspend fun closeChannel(partner: WalletAddress)


    /**
     * Initiates a channel transaction, if the channel funds are sufficient
     */
    suspend fun makeChannelTransaction(recipient: WalletAddress, amount: Double)

    /**
     * Initiates a multi-channel transaction with automatic path search,
     * iff the channel funds are sufficient for all information
     */
    suspend fun makeMultiChannelTransaction(recipient: WalletAddress, amount: Double)

    /**
     * Initiates a multi-channel transaction with a predefined path,
     * iff the channel funds are sufficient for all information
     */
    suspend fun makeMultiChannelTransaction(recipients: List<WalletAddress>, amount: Double)


    /**
     * Set the current [cycleNumber] and perform cycle-dependent tasks.
     */
    suspend fun cycle(cycleNumber: Int)

    /**
     * Add a callback function for receiving push-notifications
     */
    fun subscribe(subscriber: (Data) -> Unit)

    /**
     * Clear all subscribers
     */
    fun clearSubscribers()
}
