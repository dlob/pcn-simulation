package au.csiro.data61.pcnsimulation.configuration

/**
 * (Dynamic) properties and configuration parameters of a [Simulation]. Configurations mostly concern activities such
 * as payments, i.e. all degrees of freedom concerned with them.
 */
data class SimulationConfiguration(

        /**
         * Number of cycles a simulation offers.
         */
        val cycleCount: Int = 10,

        /**
         * Selection of routing algorithm to be used for payment routing.
         */
        val routingAlgorithm: RoutingAlgorithm = RoutingAlgorithm.BASIC,

        // --- NETWORK ---
        /**
         * Number of nodes in the payment channel network.
         */
        val networkSize: Int = 3,

        /**
         * Distribution of initial wealth across the nodes. The wealth of a node specifies the amount of capital it can
         * spend on payments or lock into channels. Different distributions may result in different overall network properties.
         */
        var networkWealthDistribution: Distribution = NormalDistribution(10.0, 0.5),

        /**
         * Distribution of initially open channels across the nodes. The chosen default will assign between 0-4 channels
         * per node with a randomly picked second node.
         */
        var networkChannelDistribution: Distribution = UniformDistribution(b = 3.0),

        /**
         * Distribution of capital that is locked into channels initially. The chosen default will assign
         * between 0-[UniformDistribution.scale] units of capital into a channel.
         */
        var networkChannelFundingDistribution: Distribution = UniformDistribution(b = 0.5),


        // --- PAYMENTS ---
        /**
         * Determines the amounts of capital that are transferred during a payment.
         */
        val paymentAmountDistribution: Distribution = NormalDistribution(0.1, 0.1),

        /**
         * Determines the regularity of payments, i.e. the ratio of reoccurring payments versus sporadic ones.
         */
        val paymentRegularityDistribution: Distribution = UniformDistribution(b = 100.0),

        /**
         * Determines the flow of payments, i.e. the directionality of capital flowing through the network from nodes
         * that act as sources of capital to nodes that act as sinks.
         */
        val paymentFlowDistribution: Distribution = NormalDistribution(6.0, 2.0),


        // --- AGENTS ---
        /**
         * Distribution of individual roles among agents.
         */
        val agentRoles: Map<AgentRole, Double> = mapOf(Pair(AgentRole.BASIC, 1.0)),

        // --- BLOCKCHAIN ---
        /**
         * Fee for transferring payments on the blockchain
         */
        val blockchainFee: Double = 0.1
)
