package au.csiro.data61.pcnsimulation.configuration

/**
 * Definition of the roles of an agent
 */
enum class AgentRole(
        /**
         * Distribution of wealth across the nodes of an agent role.
         * The wealth of a node specifies the amount of capital it can spend on payments or
         * lock into channels.
         */
        val wealthDistribution: Distribution = NormalDistribution(10.0, 5.0),

        /**
         * Distribution of channel-count across the nodes of an agent role.
         */
        val channelCountDistribution: Distribution = BetaDistribution(2.0, 5.0, 1.0, 5.0),

        /**
         * Distribution of channel-funding across the nodes of an agent role.
         */
        val channelFundingDistribution: Distribution = BetaDistribution(2.0, 8.0, 1.0, 8.0),

        /**
         * Determines if the role is a hub.
         */
        val isHub: Boolean = false,

        /**
         * Determines how strong the nodes affinity to establish channels to hubs is.
         * The value needs to be in the interval [0, 1].
         */
        val hubAffinity: Double = 0.6,

        /**
         * Distribution of the amount that a node pays.
         */
        val paymentAmountDistribution: Distribution = NormalDistribution(0.2, 1.0),

        /**
         * Distribution of payment flow: proportion of in- and outgoing payments of a node.
         *   ]-inf, 0]  only outgoing payments
         *   ]0; 1[     proportional
         *   [1; inf[   only incoming payments
         */
        val paymentFlowDistribution: Distribution = BetaDistribution(2.0, 2.0),

        /**
         * Determines the proportion of payment participation in the network.
         *   ]-inf; 0]  no payments (both incoming and outgoing)
         *   ]0; inf[   proportional
         */
        val paymentParticipationDistribution: Distribution = BetaDistribution(2.0, 2.0)
) {
    BASIC,

    PASSIVE_CONSUMER(
            channelCountDistribution = BetaDistribution(2.0, 5.0, 1.0, 4.0),
            paymentFlowDistribution = BetaDistribution(2.0, 16.0),
            paymentParticipationDistribution = BetaDistribution(2.0, 12.0)
    ),

    HEAVY_CONSUMER(
            wealthDistribution = NormalDistribution(20.0, 5.0),
            channelCountDistribution = BetaDistribution(2.0, 100.0, 1.0, Int.MAX_VALUE.toDouble()),
            channelFundingDistribution = BetaDistribution(2.0, 8.0, 1.0, 20.0),
            paymentAmountDistribution = NormalDistribution(0.5, 5.0),
            paymentParticipationDistribution = BetaDistribution(3.0, 2.0)
    ),

    MALICIOUS_USER(
            paymentParticipationDistribution = UniformDistribution(0.0) // no payment participation
    ),

    FAULTY_USER,

    SUBSCRIPTION_SERVICE(
            wealthDistribution = NormalDistribution(30.0, 5.0),
            channelCountDistribution = BetaDistribution(2.0, 5.0, 1.0, 8.0),
            paymentFlowDistribution = BetaDistribution(10.0, 2.0)
    ),

    TRADER(
            wealthDistribution = NormalDistribution(30.0, 5.0),
            channelCountDistribution = BetaDistribution(2.0, 5.0, 1.0, 8.0),
            paymentParticipationDistribution = BetaDistribution(3.0, 2.0)
    ),

    HUB(
            wealthDistribution = NormalDistribution(50.0, 5.0),
            channelCountDistribution = BetaDistribution(2.0, 10.0, 1.0, Int.MAX_VALUE.toDouble()),
            isHub = true,
            hubAffinity = 0.4,
            paymentParticipationDistribution =  UniformDistribution(0.0) // no payment participation
    ),

    SECOND_LEVEL_HUB(
            wealthDistribution = NormalDistribution(50.0, 5.0),
            channelCountDistribution = UniformDistribution(2.0, 16.0),
            channelFundingDistribution = BetaDistribution(2.0, 2.0, 1.0, 20.0),
            hubAffinity = 1.0,
            paymentParticipationDistribution =  UniformDistribution(0.0) // no payment participation
    )
}
