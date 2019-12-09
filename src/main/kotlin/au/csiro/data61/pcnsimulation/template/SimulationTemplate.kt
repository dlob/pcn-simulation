package au.csiro.data61.pcnsimulation.template

import au.csiro.data61.pcnsimulation.configuration.RoutingAlgorithm
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplate

/**
 * In contrast to a configuration, a template represents a concrete building plan for a simulation, rather than
 * a general description. For example, a configuration would specify, that each node should have an average of four
 * channels per node, while the template would already specify concrete nodes and channels.
 *
 * Templates are immutable, i.e. they cannot change their state. Templates only store the initial state of the network,
 * i.e. the state that nodes and channels are initialized with.
 */
data class SimulationTemplate(
        /**
         * Templates for agents, that control nodes and wallets.
         */
        val agents: Set<AgentTemplate>,

        /**
         * Template for payment channel network, consisting of nodes and channels.
         */
        val network: NetworkTemplate,

        /**
         * Template for each simulation cycle.
         */
        val cycles: List<CycleTemplate>,

        /**
         * Routing algorithm to be used to route payments in the network.
         */
        val routingAlgorithm: RoutingAlgorithm,

        /**
         * Fee to be paid for on-chain transactions.
         */
        val blockchainFee: Double
)
