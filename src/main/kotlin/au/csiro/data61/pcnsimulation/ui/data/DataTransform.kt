package au.csiro.data61.pcnsimulation.ui.data

import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.environment.Simulation
import au.csiro.data61.pcnsimulation.behavior.cycle.Cycle
import au.csiro.data61.pcnsimulation.behavior.cycle.PaymentCycle
import java.util.*

/**
 * Extends [Simulation]s to offer a [NetworkData] transformation
 */
fun Simulation.getData(): NetworkData {
    return NetworkData(
            this.network.nodes.values.map { it.getData() }.toSet(),
            this.network.channels.map { it.getData() }.toSet()
    )
}

/**
 * Extends [Node]s to offer a [NodeData] transformation
 */
fun Node.getData(): NodeData {
    return NodeData(
            this.name,
            this.walletAddress,
            this.socket.ipAddress,
            "%.4f".format(Locale.US, this.blockchain.balance(this.walletAddress) ?: 0.0).toDouble(),
            this.channels.map { it.otherWallet(this.walletAddress) }.toSet(),
            this.channels.map { it.otherWallet(this.walletAddress) }.toSet(),
            this.channels.map { it.balance(this.walletAddress) }.sum()
    )
}

/**
 * Extends [TransactionChannel]s to offer a [ChannelData] transformation
 */
fun TransactionChannel.getData(): ChannelData {
    return ChannelData(
            this.fromWallet,
            this.toWallet,
            this.fromBalance(),
            this.toBalance()
    )
}

/**
 * Extends [List<Cycle>]s to offer a [List<PaymentData>] transformation
 */
fun List<Cycle>.getData(firstCycle: Int = 1): List<PaymentData> = this.mapIndexedNotNull { i, cycle ->
    if (cycle is PaymentCycle) {
        PaymentData(firstCycle + i, cycle.from, cycle.to, cycle.amount)
    } else {
        null
    }
}.toList()
