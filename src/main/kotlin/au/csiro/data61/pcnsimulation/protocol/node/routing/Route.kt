package au.csiro.data61.pcnsimulation.protocol.node.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation

data class Route (
        val channels: List<DynamicChannelInformation> = listOf(),
        val peers: Map<WalletAddress, Peer> = mapOf()
)
