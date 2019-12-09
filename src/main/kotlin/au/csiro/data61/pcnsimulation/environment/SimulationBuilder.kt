package au.csiro.data61.pcnsimulation.environment

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.agent.*
import au.csiro.data61.pcnsimulation.behavior.cycle.Cycle
import au.csiro.data61.pcnsimulation.behavior.cycle.NopCycle
import au.csiro.data61.pcnsimulation.behavior.cycle.PaymentCycle
import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.configuration.RoutingAlgorithm
import au.csiro.data61.pcnsimulation.protocol.blockchain.Blockchain
import au.csiro.data61.pcnsimulation.protocol.blockchain.GenericBlockchain
import au.csiro.data61.pcnsimulation.protocol.channel.StaticChannelInformation
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannel
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.FundingTransaction
import au.csiro.data61.pcnsimulation.protocol.channel.transaction.UnconditionalOutput
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetwork
import au.csiro.data61.pcnsimulation.protocol.node.BasicNode
import au.csiro.data61.pcnsimulation.protocol.node.Node
import au.csiro.data61.pcnsimulation.protocol.node.routing.*
import au.csiro.data61.pcnsimulation.protocol.strategy.Strategy
import au.csiro.data61.pcnsimulation.template.CycleTemplateType
import au.csiro.data61.pcnsimulation.template.SimulationTemplate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Builds a simulation from a template.
 */
class SimulationBuilder {

    private lateinit var template: SimulationTemplate
    private lateinit var communication: IPNetwork

    fun template(template: SimulationTemplate) = apply { this.template = template }
    fun communication(communication: IPNetwork) = apply { this.communication = communication }

    fun build(): Simulation {
        val templateNodes = template.network.nodes.map { Pair(it.walletAddress, it) }.toMap()
        val blockchain: Blockchain = GenericBlockchain(template.blockchainFee)
        blockchain.addState(template)

        val channels: List<TransactionChannel> = template.network.channels.map {
            val fromNode = templateNodes.getValue(it.fromWallet)
            val toNode = templateNodes.getValue(it.toWallet)
            TransactionChannel(
                    FundingTransaction(
                            fromWallet = it.fromWallet,
                            toWallet = it.toWallet,
                            inputs = emptyList(),
                            outputs = listOf(UnconditionalOutput(it.fromWallet, it.fromBalance), UnconditionalOutput(it.toWallet, it.toBalance)),
                            fromPublicKey = fromNode.keyPair.public,
                            toPublicKey = toNode.keyPair.public,
                            cycle = 0
                    )
            )
        }
        val nodeChannels = channels
                .flatMap { listOf(Pair(it.fromWallet, it), Pair(it.toWallet, it)) }
                .groupBy { it.first }
                .mapValues { it.value.map { e -> e.second }.toSet() }

        // Since bigger networks have less activity per node, cycle-number based deadlines should be compensated.
        val sizeCompensation = template.agents.size / 20.0

        // BasicRouter needs concurrent accessible collections of all nodes and channels
        val basicRouterPeers = ConcurrentHashMap<WalletAddress, Peer>()
        val basicRouterChannels = ConcurrentHashMap.newKeySet<StaticChannelInformation>()
        channels.forEach { basicRouterChannels.add(StaticChannelInformation(it.fromWallet, it.fromBalance(), it.toWallet, it.toBalance())) }

        val mdartAddrSize = (log2(template.network.nodes.size.toDouble()) + 1.5).roundToInt()

        val nodes: Map<WalletAddress, Node> = template.network.nodes.map {
            val socket = communication.createSocket(it.ipAddress)
            val peer = Peer(it.name, it.keyPair.public, it.walletAddress, it.ipAddress)
            val chs = nodeChannels.getOrDefault(it.walletAddress, emptySet())
            val routerChannels = chs.map { c -> StaticChannelInformation(c.fromWallet, c.fromBalance(), c.toWallet, c.toBalance()) }.toSet()
            val routerPeers = chs.map { c ->
                val n = templateNodes.getValue(c.otherWallet(it.walletAddress))
                Pair(n.walletAddress, Peer(n.name, n.keyPair.public, n.walletAddress, n.ipAddress))
            }.toMap()
            basicRouterPeers[it.walletAddress] = peer
            val strategy = Strategy()
            val router = when(template.routingAlgorithm) {
                RoutingAlgorithm.BASIC -> BasicRouter(peer, socket, strategy, basicRouterPeers, basicRouterChannels)
                RoutingAlgorithm.TERP -> TERPRouter(peer, socket, strategy, routerPeers, routerChannels, 10, (20 * sizeCompensation).toInt(), 30)
                RoutingAlgorithm.MDART -> MDARTRouter(peer, socket, strategy, routerPeers, routerChannels, mdartAddrSize, 5, mdartAddrSize  * 2, 30)
                RoutingAlgorithm.ETORA -> ETORARouter(peer, socket, strategy, routerPeers, routerChannels, 16,30)
            }
            val node = BasicNode(it.name, it.keyPair, it.walletAddress, blockchain, strategy, chs, socket, router)
            Pair(it.walletAddress, node)
        }.toMap()

        val agents: Map<String, Agent> = template.agents.map {
            val node = nodes.values.first { n -> n.name == it.name }
            Pair(it.name,
                    when (it.role) {
                        AgentRole.BASIC -> BasicAgent(node)
                        AgentRole.PASSIVE_CONSUMER -> PassiveConsumerAgent(node, sizeCompensation)
                        AgentRole.HEAVY_CONSUMER -> HeavyConsumerAgent(node, sizeCompensation)
                        AgentRole.MALICIOUS_USER -> MaliciousUserAgent(node, sizeCompensation)
                        AgentRole.FAULTY_USER -> FaultyUserAgent(node, sizeCompensation)
                        AgentRole.SUBSCRIPTION_SERVICE -> SubscriptionServiceAgent(node, sizeCompensation)
                        AgentRole.TRADER -> TraderAgent(node, sizeCompensation)
                        AgentRole.HUB -> HubAgent(node, sizeCompensation)
                        AgentRole.SECOND_LEVEL_HUB -> SecondLevelHubAgent(node, sizeCompensation)
                    })
        }.toMap()

        val cycles: List<Cycle> = template.cycles.map {
            when(it.type) {
                CycleTemplateType.PAYMENT -> PaymentCycle(agents.getValue(nodes.getValue(it.from!!).name), it.from, it.to!!, it.amount!!, it.urgent!!)
                CycleTemplateType.NOP -> NopCycle()
            }
        }

        return Simulation(
                Simulation.Network(
                        nodes,
                        channels
                ),
                agents.values.toList(),
                cycles,
                blockchain,
                communication
        )
    }
}
