package au.csiro.data61.pcnsimulation.behavior.agent

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.node.Node

/**
 * An agent controls a node in the transaction network and uses a certain strategy to reach its objectives.
 * It is acting rationally in that it makes decisions based on the expected profit.
 */
interface Agent {
    /**
     * The node that the agent controls.
     */
    val node: Node

    /**
     * Add a payment.
     */
    suspend fun addPayment(from: WalletAddress, to: WalletAddress, amount: Double, urgent: Boolean)

    /**
     * Execute the agents' logic for one cycle.
     */
    suspend fun cycle(cycle: Int)
}
