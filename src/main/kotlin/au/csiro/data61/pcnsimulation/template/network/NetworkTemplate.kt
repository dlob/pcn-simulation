package au.csiro.data61.pcnsimulation.template.network

/**
 * In contrast to a configuration, a template represents a concrete building plan for a network, rather than
 * a general description. For example, a configuration would specify, that each node should have an average of four
 * channels per node, while the template would already specify concrete nodes and channels.
 *
 * Templates are immutable, i.e. they cannot change their state. Templates only store the initial state of the network,
 * i.e. the state that nodes and channels are initialized with.
 */
data class NetworkTemplate(
        /**
         * Templates for each node
         */
        val nodes: Set<NodeTemplate>,

        /**
         * Templates for each channel
         */
        val channels: Set<ChannelTemplate>
)
