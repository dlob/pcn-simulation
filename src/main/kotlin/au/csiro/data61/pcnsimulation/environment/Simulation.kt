package au.csiro.data61.pcnsimulation.environment

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.agent.Agent
import au.csiro.data61.pcnsimulation.protocol.blockchain.Blockchain
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetwork
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.behavior.cycle.Cycle

/**
 * Represents an instance of a simulation.
 */
data class Simulation(

        /**
         * Simulated payment channel network representation.
         */
        var network: Network,

        /**
         * Agents, that operate the individual nodes.
         */
        var agents: List<Agent>,

        /**
         * Payments and other operations that are simulated.
         */
        var cycles: List<Cycle>,

        /**
         * Underlying blockchain of the payment channels.
         */
        val blockchain: Blockchain,

        /**
         * Communication network used by the [network] during execution.
         */
        val communication: IPNetwork
) {
        data class Network (
                /**
                 * All nodes of the simulation.
                 */
                val nodes: Map<WalletAddress,Node>,

                /**
                 * All channels of the simulation.
                 */
                val channels: List<TransactionChannel>
        )
}
