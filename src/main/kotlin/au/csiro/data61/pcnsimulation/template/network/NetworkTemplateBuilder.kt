package au.csiro.data61.pcnsimulation.template.network

import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.configuration.Distribution
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.template.AgentTemplate
import au.csiro.data61.pcnsimulation.template.IPAddressGenerator
import au.csiro.data61.pcnsimulation.template.NameGenerator
import au.csiro.data61.pcnsimulation.util.Crypto
import kotlin.random.Random

/**
 * Build an instance of [NetworkTemplate].
 */
class NetworkTemplateBuilder {
    private var useRoles: Boolean = false
    private var linearGraph: Boolean = false
    private var size: Int? = null
    private var agents: Map<String, AgentTemplate>? = null
    private var wealthDistribution: Distribution? = null
    private var channelDistribution: Distribution? = null
    private var channelFundingDistribution: Distribution? = null

    fun enableRoles() = apply { this.useRoles = true }
    fun enableLinearGraph() = apply { this.linearGraph = true }
    fun size(size: Int) = apply { this.size = size }
    fun agents(agents: Map<String, AgentTemplate>) = apply { this.agents = agents; this.size = agents.size }
    fun wealthDistribution(wealthDistribution: Distribution) = apply { this.wealthDistribution = wealthDistribution }
    fun channelDistribution(channelDistribution: Distribution) = apply { this.channelDistribution = channelDistribution }
    fun channelFundingDistribution(channelFundingDistribution: Distribution) = apply { this.channelFundingDistribution = channelFundingDistribution }

    companion object {
        private val log by logger()
    }

    /**
     * Build an instance of [NetworkTemplate]. The template's properties will adhere
     * the parameters and statistical distributions provided in the configuration.
     */
    fun build() : NetworkTemplate {
        return if (useRoles) {
            if (agents == null) error("agents are required.")
            val nodes = generateNodes(agents!!)
            val channels = generateChannels(nodes, agents!!)
            NetworkTemplate(nodes.values.toSet(), channels)
        } else {
            if (size == null) error("size is required.")
            if (wealthDistribution == null) error("wealthDistribution is required.")
            val ipAddressGenerator = IPAddressGenerator()
            val names = if (agents == null) {
                val nameGenerator = NameGenerator()
                (0 until size!!).map { nameGenerator.next() }
            } else {
                agents!!.keys
            }
            val nodes = names.map {
                val name = it
                val keyPair = Crypto.generateKeyPair()
                val walletAddress = Crypto.createWallet(keyPair.public)
                val ipAddress = ipAddressGenerator.next()
                val wealth = wealthDistribution!!.sample()
                NodeTemplate(name, keyPair, ipAddress, walletAddress, wealth)
            }
            if (channelFundingDistribution == null) error("channelFundingDistribution is required.")
            val channels = if (linearGraph) {
                generateLinearGraphChannels(nodes)
            } else {
                if (channelDistribution == null) error("channelDistribution is required.")
                generateBasicChannels(nodes)
            }
            NetworkTemplate(nodes.toSet(), channels)
        }
    }

    private fun generateLinearGraphChannels(nodes: List<NodeTemplate>): Set<ChannelTemplate> {
        return nodes.zipWithNext().map {
            ChannelTemplate(
                    it.first.walletAddress,
                    it.second.walletAddress,
                    channelFundingDistribution!!.sample(),
                    channelFundingDistribution!!.sample()
            )
        }.toSet()
    }

    private fun generateBasicChannels(nodes: List<NodeTemplate>): Set<ChannelTemplate> {
        val result = mutableSetOf<ChannelTemplate>()
        for (node in nodes) {
            /*
             * divided by 2, because a node can be selected both as [node] or [y]
             */
            val noOfChannels = (channelDistribution!!.sample() / 2.0 + 0.5).toInt()
            for (i in 0..noOfChannels) {
                val other = nodes[Random.nextInt(nodes.size)]
                if (node != other && result
                                .none {
                                    it.fromWallet == node.walletAddress && it.toWallet == other.walletAddress
                                            || it.toWallet == node.walletAddress && it.fromWallet == other.walletAddress
                                }) {
                    result.add(ChannelTemplate(
                            node.walletAddress,
                            other.walletAddress,
                            channelFundingDistribution!!.sample() * node.walletBalance,
                            channelFundingDistribution!!.sample() * other.walletBalance
                    ))
                }
            }
        }
        return result
    }

    /**
     * Create channels between nodes randomly.
     * Based on the roles configuration, channels to hubs are prioritised.
     *
     * @param nodes     map of node, key is the name of the agent.
     * @param agents    map of agents, key is the name ot the agent.
     *
     * @return          set of channels, that have been created.
     */
    private fun generateChannels(nodes: Map<String, NodeTemplate>, agents: Map<String, AgentTemplate>): Set<ChannelTemplate> {
        val nodeContexts = nodes.map {
            val role = agents.getValue(it.key).role
            NodeContext(
                    it.key,
                    role,
                    it.value.walletBalance,
                    Math.round(role.channelCountDistribution.sample()).toInt()
            )
        }.toList()


        val channels = mutableSetOf<ChannelTemplate>()
        var unsuccessfulRounds = 0
        do {
            unsuccessfulRounds++
            val n1 = nodeContexts.random()
            val n2 = nodeContexts.random()
            // Nodes are distinct
            if (n1 == n2) continue
            // Channel does not already exist
            if (n1.channels.contains(n2.name)) continue

            val n1Satisfaction = Random.nextDouble() * (if (n2.agent.isHub) n1.agent.hubAffinity else 1.0 - n1.agent.hubAffinity)
            if (n1Satisfaction == 0.0) continue
            val n2Satisfaction = Random.nextDouble() * (if (n1.agent.isHub) n2.agent.hubAffinity else 1.0 - n2.agent.hubAffinity)
            if (n2Satisfaction == 0.0) continue

            val satisfaction = (n1Satisfaction + n2Satisfaction) / 2
            val n1Funding = n1.agent.channelFundingDistribution.sample()
            val n2Funding = n1.agent.channelFundingDistribution.sample() // Originating node dictates channel funding
            var n1ReplaceableChannels = emptySet<ChannelContext>()
            var n2ReplaceableChannels = emptySet<ChannelContext>()

            // n1 wants another channel and has enough funding
            if (n1.remainingChannelCount() == 0 || n1.remainingWealth() < n1Funding) {
                n1ReplaceableChannels = n1.replaceableChannels(satisfaction, n1Funding)
                if (n1ReplaceableChannels.isEmpty()) continue
            }
            // n2 wants another channel and has enough funding
            if (n2.remainingChannelCount() == 0 || n2.remainingWealth() < n2Funding) {
                n2ReplaceableChannels = n2.replaceableChannels(satisfaction, n1Funding)
                if (n2ReplaceableChannels.isEmpty()) continue
            }
            // Replace channels of n1 with low satisfaction with new channel
            for (c in n1ReplaceableChannels) {
                channels.remove(c.template)
                n1.channels.remove(c.partner.name)
                c.partner.channels.remove(n1.name)
            }
            // Replace channels of n2 with low satisfaction with new channel
            for (c in n2ReplaceableChannels) {
                channels.remove(c.template)
                n2.channels.remove(c.partner.name)
                c.partner.channels.remove(n2.name)
            }

            // Create channel template
            val c = ChannelTemplate(
                    nodes.getValue(n1.name).walletAddress,
                    nodes.getValue(n2.name).walletAddress,
                    n1Funding,
                    n2Funding
            )
            n1.channels[n2.name] = ChannelContext(n2, satisfaction, n1Funding, c)
            n2.channels[n1.name] = ChannelContext(n1, satisfaction, n2Funding, c)
            channels.add(c)
            unsuccessfulRounds = 0
        } while (unsuccessfulRounds < nodeContexts.size * 2)

        // report nodes with low satisfaction
        for (n in nodeContexts) {
            if (n.satisfaction() < 0.1) {
                log.info("${n.name}: low satisfaction (${n.remainingChannelCount()};${n.remainingWealth()})")
            }
        }

        return channels
    }

    private data class NodeContext (
            val name: String,
            val agent: AgentRole,
            val wealth: Double,
            val channelCount: Int,
            val channels: MutableMap<String, ChannelContext> = mutableMapOf()
    ) {
        fun remainingWealth() : Double = wealth - channels.values.sumByDouble { it.funding }
        fun remainingChannelCount() : Int = channelCount - channels.size
        fun satisfaction() : Double = channels.values.sumByDouble { it.satisfaction } / channels.size
        // Get channels that can be replaced by a new channel to gain satisfaction
        fun replaceableChannels(satisfaction: Double, funding: Double) : Set<ChannelContext> {
            val channelList = channels.values.sortedBy { it.satisfaction }.toMutableList()
            val result = mutableSetOf<ChannelContext>()
            while (channelList.sumByDouble { it.funding } + funding > wealth && channels.size > channelCount && channelList.size > 0) {
                result.add(channelList.removeAt(0))
            }
            val newSatisfaction = (channelList.sumByDouble { it.satisfaction } + satisfaction) / (channelList.size + 1)
            return if (newSatisfaction > satisfaction()) {
                result
            } else {
                emptySet()
            }
        }
    }

    private data class ChannelContext (
            val partner: NodeContext,
            val satisfaction: Double,
            val funding: Double,
            val template: ChannelTemplate
    )

    /**
     * Generates nodes based on the given agents.
     * Based on the simulators limitations, there is exactly one node per agent.
     * Their relation is indicated by a matching name property.
     *
     * @param agents    map of agent templates with the name as key
     *
     * @return          map of nodes with the name as key
     */
    private fun generateNodes(agents: Map<String, AgentTemplate>): Map<String, NodeTemplate> {
        val ipAddressGenerator = IPAddressGenerator()
        return agents.map {
            val name = it.key
            val keyPair = Crypto.generateKeyPair()
            val walletAddress = Crypto.createWallet(keyPair.public)
            val ipAddress = ipAddressGenerator.next()
            val wealth = it.value.role.wealthDistribution.sample()
            name to NodeTemplate(name, keyPair, ipAddress, walletAddress, wealth)
        }.toMap()
    }
}
