package au.csiro.data61.pcnsimulation.ui.data

/**
 * Data about a transaction channel network offered by the API
 */
data class NetworkData(
        var nodes: Set<NodeData>,
        var channels: Set<ChannelData>
)
