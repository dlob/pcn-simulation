package au.csiro.data61.pcnsimulation.protocol.node

import au.csiro.data61.pcnsimulation.Hash
import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.blockchain.Blockchain
import au.csiro.data61.pcnsimulation.protocol.blockchain.BlockchainException
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.CommitmentTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.Transaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetworkException
import au.csiro.data61.pcnsimulation.protocol.communication.IPPacket
import au.csiro.data61.pcnsimulation.protocol.communication.IPSocket
import au.csiro.data61.pcnsimulation.protocol.message.notification.ChannelUpdateNotification
import au.csiro.data61.pcnsimulation.protocol.message.request.*
import au.csiro.data61.pcnsimulation.protocol.message.response.*
import au.csiro.data61.pcnsimulation.protocol.node.routing.Peer
import au.csiro.data61.pcnsimulation.protocol.node.routing.Router
import au.csiro.data61.pcnsimulation.protocol.strategy.ApprovalResult
import au.csiro.data61.pcnsimulation.protocol.strategy.RoutingStrategy
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import au.csiro.data61.pcnsimulation.ui.data.ChannelData
import au.csiro.data61.pcnsimulation.ui.data.Data
import au.csiro.data61.pcnsimulation.ui.data.PaymentData
import au.csiro.data61.pcnsimulation.ui.data.getData
import au.csiro.data61.pcnsimulation.util.Crypto
import java.security.KeyPair

/**
 * Basic implementation of a node in a transaction channel network
 */
class BasicNode(
        override val name: String,
        override val keyPair: KeyPair,
        override val walletAddress: WalletAddress,
        override val blockchain: Blockchain,
        override val strategy: Strategy,
        initialChannels: Set<TransactionChannel>,
        override val socket: IPSocket,
        override val router: Router
) : Node {

    companion object {
        val log by logger()
    }

    override val channels = initialChannels.toMutableList()

    private var cycleNumber = 0
    private val subscribers = mutableListOf<(Data) -> Unit>()
    private val expectedTransactions = mutableMapOf<Hash, Pair<String, Double>>()

    init {
        socket.registerReceiveCallback { this.handle(it) }
    }

    override suspend fun goOnline() {
        socket.connect()
    }

    override suspend fun goOffline() {
        socket.close()
    }

    override suspend fun openChannel(receiver: WalletAddress, aBound: Double, bBound: Double) {
        if (blockchain.balance(walletAddress)!! >= aBound + blockchain.fee / 2
                && blockchain.balance(receiver)!! >= bBound + blockchain.fee / 2
        ) {
            if (findChannelByWallets(walletAddress, receiver) != null) {
                throw NodeException("A channel between $walletAddress and $receiver is already established.")
            }
            val receiverPeer = router.findPeer(receiver)
            var fundingTransaction = FundingTransaction(
                    fromWallet = walletAddress,
                    fromPublicKey = keyPair.public,
                    toWallet = receiver,
                    toPublicKey = receiverPeer.publicKey,
                    inputs = emptyList(),
                    outputs = listOf(UnconditionalOutput(walletAddress, aBound), UnconditionalOutput(receiver, bBound)),
                    cycle = cycleNumber
            )
            fundingTransaction = fundingTransaction.copy(fromSignature = Crypto.sign(fundingTransaction, keyPair.private))

            lateinit var response: AcceptChannelResponse
            try {
                response = socket.request(receiverPeer.ipAddress, OpenChannelRequest(fundingTransaction), 500) as AcceptChannelResponse
                router.report("OpenChannelRequest", receiverPeer.walletAddress, true)
            } catch (e: IPNetworkException) {
                router.report("OpenChannelRequest", receiverPeer.walletAddress, false)
                throw NodeCommunicationException(name, e)
            }

            val funding = response.fundingTransaction
            if (funding != null) {
                try {
                    blockchain.openChannel(funding)
                    val channel = TransactionChannel(funding)
                    channels.add(channel)
                    val info = StaticChannelInformation(funding.fromWallet, funding.fromBalance(), funding.toWallet, funding.toBalance())
                    router.addOrUpdateChannel(info, receiverPeer)
                    socket.notify(receiverPeer.ipAddress, ChannelUpdateNotification(
                            info,
                            Peer(name, keyPair.public, walletAddress, socket.ipAddress),
                            ChannelUpdateNotification.ChannelState.OPEN
                    ))
                    subscribers.forEach { it(Data("open-channel", channel.getData())) }
                } catch (e: BlockchainException) {
                    throw NodeException("Channel could not be opened: ${e.message}", e)
                }
            } else {
                subscribers.forEach { it(Data("open-channel-declined", Pair(walletAddress, receiver))) }
                throw NodeException("Channel request was declined")
            }
        } else {
            throw NodeException("Not enough funds to open channel")
        }
    }

    override suspend fun closeChannel(partner: WalletAddress) {
        val channel = findChannelByWallets(walletAddress, partner)!!
        val partnerPeer = router.findPeer(partner)
        lateinit var response: CloseChannelResponse
        try {
            response = socket.request(partnerPeer.ipAddress, CloseChannelRequest(transaction = channel.getLatest()), 500) as CloseChannelResponse
            router.report("CloseChannelRequest", partner, true)
        } catch (e: IPNetworkException) {
            router.report("CloseChannelRequest", partner, false)
            throw NodeCommunicationException(name, e)
        }

        if (response.transaction != null && response.transaction == channel.getLatest()) {
            try {
                blockchain.closeChannel(response.transaction!!)
                channels.remove(channel)
                router.removeChannel(channel.otherWallet(walletAddress))
                val info = StaticChannelInformation(channel.fromWallet, 0.0, channel.toWallet, 0.0)
                socket.notify(partnerPeer.ipAddress, ChannelUpdateNotification(
                        info,
                        Peer(name, keyPair.public, walletAddress, socket.ipAddress),
                        ChannelUpdateNotification.ChannelState.CLOSED
                ))
                subscribers.forEach { it(Data("close-channel", Pair(channel.fromWallet, channel.toWallet))) }
            } catch (e: BlockchainException) {
                throw NodeException("Channel could not be closed: ${e.message}", e)
            }
        }
    }

    override suspend fun makeChannelTransaction(recipient: WalletAddress, amount: Double) {
        subscribers.forEach { it(Data("single-payment", PaymentData(cycleNumber, walletAddress, recipient, amount))) }
        val channel = findChannelByWallets(walletAddress, recipient)
        if (channel != null) {
            val transaction = channel.createCommitmentTransaction(walletAddress, amount, keyPair.private, cycleNumber)
            if (transaction != null) {
                val peer = router.findPeer(recipient)
                lateinit var response: ReceivedTransactionResponse
                try {
                    response = socket.request(peer.ipAddress, TransactionRequest(transaction), 500) as ReceivedTransactionResponse
                } catch (e: IPNetworkException) {
                    subscribers.forEach { it(Data("single-payment-failed", Pair(cycleNumber, e.message))) }
                    throw NodeCommunicationException(name, e)
                }
                addToChannel(response.transaction)
                subscribers.forEach {
                    it(Data("channel", ChannelData(transaction.fromWallet, transaction.toWallet, transaction.fromBalance(), transaction.toBalance())))
                    it(Data("single-payment-successful", PaymentData(cycleNumber, walletAddress, recipient, amount)))
                }
            } else {
                subscribers.forEach { it(Data("single-payment-failed", Pair(cycleNumber, "Not enough funds in the channel"))) }
                throw TransactionFailedException(walletAddress, recipient, amount, "Not enough funds in the channel.")
            }
        } else {
            subscribers.forEach { it(Data("single-payment-failed", Pair(cycleNumber, "Channel from $walletAddress to $recipient doesn't exist."))) }
            throw TransactionFailedException(walletAddress, recipient, amount, "Channel to recipient doesn't exist.")
        }
    }

    override suspend fun makeMultiChannelTransaction(recipient: WalletAddress, amount: Double) {
        subscribers.forEach { it(Data("multi-payment", PaymentData(cycleNumber, walletAddress, recipient, amount))) }
        try {
            val routes = router.findRoutes(recipient)
            val selectedRoute = strategy.route.selectRoute(routes, walletAddress, recipient, amount)
            makeRouteTransaction(selectedRoute, amount)
        } catch(e: NodeException) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, "No route from $walletAddress to $recipient found."))) }
            throw NodeException("No route from $walletAddress to $recipient found.", e)
        }
    }

    override suspend fun makeMultiChannelTransaction(recipients: List<WalletAddress>, amount: Double) {
        subscribers.forEach { it(Data("multi-payment", PaymentData(cycleNumber, walletAddress, recipients.last(), amount))) }
        try {
            val route = router.findRoute(recipients)
            val selectedRoute = strategy.route.selectRoute(setOf(route), walletAddress, recipients.last(), amount)
            makeRouteTransaction(selectedRoute, amount)
        } catch(e: NodeException) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, "No route from $walletAddress to ${recipients.last()} found."))) }
            throw NodeException("No route from $walletAddress to ${recipients.last()} found.", e)
        }
    }

    private suspend fun makeRouteTransaction(solution: RoutingStrategy.RouteSelection, amount: Double) {
        val route = solution.routes.first()
        if (solution.routes.size > 1) {
            throw NodeException("Splitting payments to multiple Routes is not supported.")
        }
        subscribers.forEach {
            it(Data("payment-fees", Pair(cycleNumber, solution.overallFees)))
        }
        val firstPeer = route.peers[route.channels.first().toWallet]!!
        val lastPeer = route.peers[route.channels.last().toWallet]!!
        val recipients = route.channels.map { it.toWallet }.toList()

        subscribers.forEach { it(Data("multi-channel", Pair(cycleNumber, listOf(walletAddress) + recipients))) }

        val channel = findChannelByWallets(walletAddress, firstPeer.walletAddress)
        if (channel == null) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, "Channel to first recipient doesn't exist."))) }
            throw TransactionFailedException(walletAddress, firstPeer.walletAddress, amount, "Channel to first recipient doesn't exist.")
        }

        router.report("MultiChannelTransactionLiquidity", firstPeer.walletAddress, channel.balance(walletAddress) >= route.channels.first().liquidity)

        lateinit var hashLockResponse: MultiChannelTransactionHashLockResponse
        try {
            hashLockResponse = socket.request(lastPeer.ipAddress, MultiChannelTransactionHashLockRequest(amount), 500) as MultiChannelTransactionHashLockResponse
        } catch (e: IPNetworkException) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, e.message))) }
            throw NodeCommunicationException(name, e)
        }

        val lockedTransaction = channel.createLockedTransaction(walletAddress, solution.overallFees + amount, hashLockResponse.hashLock, hashLockResponse.unlock, keyPair.private, cycleNumber)
        if (lockedTransaction == null) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, "Insufficient funds for multi-channel transaction."))) }
            throw TransactionFailedException(walletAddress, firstPeer.walletAddress, amount, "Insufficient funds for multi-channel transaction.")
        }

        lateinit var response: MultiChannelTransactionResponse
        try {
            response = socket.request(firstPeer.ipAddress, MultiChannelTransactionRequest(
                    lockedTransaction = lockedTransaction,
                    hops = recipients.drop(1),
                    hashLock = hashLockResponse.hashLock,
                    unlock = hashLockResponse.unlock
            ), 500) as MultiChannelTransactionResponse
            router.report("MultiChannelTransactionRequest", firstPeer.walletAddress, true)
        } catch (e: IPNetworkException) {
            router.report("MultiChannelTransactionRequest", firstPeer.walletAddress, false)
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, e.message))) }
            throw NodeCommunicationException(name, e)
        }

        if (response.failed) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, "Multi channel transaction execution failed."))) }
            throw TransactionFailedException(walletAddress, lastPeer.walletAddress, amount, "Multi channel transaction execution failed.")
        }
        channel.transactions.add(response.signedTransaction!!)
        if (!channel.claim(response.hashLock, response.secret)) {
            subscribers.forEach { it(Data("multi-payment-failed", Pair(cycleNumber, "Could not unlock one or more locked transactions."))) }
            throw NodeException("Could not unlock one or more locked transactions.")
        }
        subscribers.forEach {
            it(Data("multi-payment-successful", PaymentData(cycleNumber, walletAddress, lastPeer.walletAddress, lockedTransaction.outputs.last().amount)))
        }
    }

    override suspend fun cycle(cycleNumber: Int) {
        this.cycleNumber = cycleNumber
        router.cycle(cycleNumber)
    }

    override fun subscribe(subscriber: (Data) -> Unit) {
        subscribers.add(subscriber)
    }

    override fun clearSubscribers() {
        subscribers.clear()
    }

    private suspend fun handle(packet: IPPacket) {
        val message = packet.payload
        when (message) {
            is PingRequest -> {
                socket.respond(packet.sender, packet.id, PingResponse())
            }
            is OpenChannelRequest -> {
                val approval = strategy.channel.accept(this, message)
                if (approval != ApprovalResult.IGNORE) {
                    val funding = if (approval == ApprovalResult.APPROVE && message.fundingTransaction.toBalance() <= blockchain.balance(walletAddress)!!) {
                        val t = message.fundingTransaction.copy(toSignature = Crypto.sign(message.fundingTransaction, keyPair.private))
                        addToChannel(t)
                        t
                    } else {
                        null
                    }
                    socket.respond(packet.sender, packet.id, AcceptChannelResponse(funding))
                }
            }
            is CloseChannelRequest -> {
                val approval = strategy.channel.accept(this, message)
                if (approval != ApprovalResult.IGNORE) {
                    val channel = findChannelByWallets(message.transaction.fromWallet, message.transaction.toWallet)!!
                    if (channel.counter() == message.transaction.counter) {
                        channels.remove(channel)
                        socket.respond(packet.sender, packet.id, CloseChannelResponse(message.transaction))
                    }
                }
            }
            is ChannelUpdateNotification -> {
                if (message.state == ChannelUpdateNotification.ChannelState.OPEN) {
                    router.addOrUpdateChannel(message.channel, message.peer)
                } else {
                    router.removeChannel(setOf(message.channel.fromWallet, message.channel.toWallet).first { it != walletAddress } )
                }
            }
            is TransactionRequest -> {
                val transaction = if (message.transaction.fromWallet == walletAddress) {
                    message.transaction.copy(fromSignature = Crypto.sign(message.transaction, keyPair.private))
                } else {
                    message.transaction.copy(toSignature = Crypto.sign(message.transaction, keyPair.private))
                }
                addToChannel(transaction)
                socket.respond(packet.sender, packet.id, ReceivedTransactionResponse(transaction))
            }
            is MultiChannelTransactionHashLockRequest -> {
                val secret = Crypto.generateSecret()
                val hashLock = Crypto.sha256(secret)
                val unlock = Crypto::sha256
                expectedTransactions[hashLock] = Pair(secret, message.amount)
                socket.respond(packet.sender, packet.id, MultiChannelTransactionHashLockResponse(hashLock, unlock))
            }
            is MultiChannelTransactionRequest -> {
                val isIntermediate = message.hops.isNotEmpty()
                if (isIntermediate) {
                    val approval = strategy.paymentRelaying.accept(message.lockedTransaction.otherWallet(walletAddress), message.hops.first())
                    if (approval == ApprovalResult.DECLINE) {
                        subscribers.forEach {
                            it(Data("relaying-declined", Pair(walletAddress, message.hops.first())))
                        }
                        socket.respond(packet.sender, packet.id, MultiChannelTransactionResponse("", "", failed = true))
                        return
                    } else if (approval == ApprovalResult.IGNORE) {
                        return
                    }
                }

                var incoming = message.lockedTransaction
                incoming = if (incoming.fromWallet == walletAddress) {
                    incoming.copy(fromSignature = Crypto.sign(incoming, keyPair.private))
                } else {
                    incoming.copy(toSignature = Crypto.sign(incoming, keyPair.private))
                }
                val incomingChannel = findChannelByWallets(walletAddress, incoming.otherWallet(walletAddress))!!
                val incomingChannelBalanceBefore = incomingChannel.balance(walletAddress)
                incomingChannel.transactions.add(incoming)

                /*
                 * Intermediate node
                 */
                if (isIntermediate) {
                    val hopPeer = router.findPeer(message.hops.first())
                    val outgoingChannel = findChannelByWallets(walletAddress, hopPeer.walletAddress)!!
                    val incomingAmount = incoming.getByHashLock(message.hashLock)?.amount ?: 0.0
                    val fee = strategy.fee.determine(hopPeer.walletAddress)
                    val outgoingAmount = fee.removeFromPayment(incomingAmount)
                    router.report("MultiChannelTransactionLiquidity", hopPeer.walletAddress, outgoingChannel.balance(walletAddress) >= outgoingAmount)
                    val outgoing = outgoingChannel.createLockedTransaction(walletAddress, outgoingAmount, message.hashLock, message.unlock, keyPair.private, cycleNumber)
                    if (outgoing == null) {
                        throw NodeException("Transaction creation for outgoing channel failed.")
                    }

                    lateinit var response: MultiChannelTransactionResponse
                    try {
                        response = socket.request(hopPeer.ipAddress, MultiChannelTransactionRequest(
                                outgoing,
                                message.hops.drop(1),
                                message.hashLock,
                                message.unlock
                        ), 500) as MultiChannelTransactionResponse
                        router.report("MultiChannelTransactionRequest", hopPeer.walletAddress, true)
                    } catch (e: IPNetworkException) {
                        router.report("MultiChannelTransactionRequest", hopPeer.walletAddress, false)
                        log.warn(e.message)
                        response = MultiChannelTransactionResponse("", "", failed = true)
                    }

                    if (response.failed) {
                        socket.respond(packet.sender, packet.id, response)
                    } else {
                        outgoingChannel.transactions.add(response.signedTransaction!!)

                        /*
                         * Claim locked transactions. For intermediate nodes, this is both incoming and outgoing transactions.
                         */
                        if (incomingChannel.claim(response.hashLock, response.secret) &&
                                outgoingChannel.claim(response.hashLock, response.secret)) {

                            socket.respond(packet.sender, packet.id, response.copy(signedTransaction = incoming.copy()))
                            incomingChannel.let { t ->
                                subscribers.forEach {
                                    it(Data("unlocked", ChannelData(t.fromWallet, t.toWallet, t.fromBalance(), t.toBalance())))
                                }
                            }
                        } else {
                            throw NodeException("Could not unlock one or more locked transactions.")
                        }
                    }
                }

                /*
                 * Final recipient
                 */
                else {
                    val expectedTransaction = expectedTransactions[message.hashLock]
                    if (expectedTransaction == null) {
                        throw NodeException("Unexpected transaction request received.")
                    }
                    expectedTransactions.remove(message.hashLock)

                    /*
                     * Claim locked transaction.
                     */
                    val secret = expectedTransaction.first
                    if (incomingChannel.claim(message.hashLock, secret)) {
                        // Check for the right amount to be received
                        val amount = expectedTransaction.second
                        if (Math.abs(incomingChannel.balance(walletAddress) - incomingChannelBalanceBefore - amount) > 0.000001) {
                            log.error("The received amount is wrong.")
                        }
                        // Return the hashlock
                        socket.respond(packet.sender, packet.id, MultiChannelTransactionResponse(message.hashLock, secret, incoming.copy()))
                        incomingChannel.let { t ->
                            subscribers.forEach {
                                it(Data("unlocked", ChannelData(t.fromWallet, t.toWallet, t.fromBalance(), t.toBalance())))
                            }
                        }
                    } else {
                        throw NodeException("Could not unlock one or more locked transactions")
                    }
                }
            }
        }
    }


    private fun findChannelByWallets(aWallet: WalletAddress, bWallet: WalletAddress): TransactionChannel? {
        return channels.find {
            (it.fromWallet == aWallet && it.toWallet == bWallet) || (it.fromWallet == bWallet && it.toWallet == aWallet)
        }
    }

    private fun addToChannel(transaction: Transaction) {
        when (transaction) {
            is FundingTransaction -> channels.add(TransactionChannel(transaction, mutableListOf()))
            is CommitmentTransaction -> {
                val channel = findChannelByWallets(transaction.fromWallet, transaction.toWallet)!!
                channel.transactions.add(transaction)
            }
        }
    }
}
