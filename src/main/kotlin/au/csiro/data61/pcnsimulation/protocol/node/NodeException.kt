package au.csiro.data61.pcnsimulation.protocol.node

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetworkException


open class NodeException(errorMessage: String, cause: Throwable? = null) : Exception(errorMessage, cause)

class TransactionFailedException(from: WalletAddress, to: WalletAddress, amount: Double, message: String) : NodeException("Payment from $from to $to with amount $amount failed: $message")

class NoRouteFoundException(from: WalletAddress, to: WalletAddress, amount: Double) : NodeException("No route was found for payment from $from to $to with amount $amount")

class NodeCommunicationException(name: String, cause: IPNetworkException) : NodeException(name + ": " + cause.message!!, cause)
