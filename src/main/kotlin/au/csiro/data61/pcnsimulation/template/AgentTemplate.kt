package au.csiro.data61.pcnsimulation.template

import au.csiro.data61.pcnsimulation.configuration.AgentRole

/**
 * Template of an agent.
 * An agent acts behavioural according to its role. It controls its node and wallet and issues payments.
 */
data class AgentTemplate(
        /**
         * Name of the agent.
         */
        val name: String,

        /**
         * Role of the agent.
         */
        val role: AgentRole
)
