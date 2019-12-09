package au.csiro.data61.pcnsimulation.template

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.configuration.*
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplateBuilder
import au.csiro.data61.pcnsimulation.template.network.NodeTemplate
import java.util.*

class SimulationTemplateBuilder {

    private var config = SimulationConfiguration()
    private var tiny = false

    fun config(config: SimulationConfiguration) = apply { this.config = config }

    fun roles(roles: Map<AgentRole, Double>) = apply { this.config = this.config.copy(agentRoles = roles)}

    fun tiny() = apply { this.tiny = true }
    fun small() = apply { this.config = config.copy(networkSize = 10, cycleCount = 50) }
    fun medium() = apply { this.config = config.copy(networkSize = 100, cycleCount = 100) }
    fun large() = apply { this.config = config.copy(networkSize = 1000, cycleCount = 200) }

    companion object {
        private val log by logger()
    }

    fun build() : SimulationTemplate {
        if (tiny) {
            return buildTiny()
        }
        val legacyGeneratorEnabled = config.agentRoles.all { it.key == AgentRole.BASIC }
        // Build agents
        val agents = generateAgents()
        // Build network
        val networkBuilder = NetworkTemplateBuilder()
                .agents(agents)
                .wealthDistribution(config.networkWealthDistribution)
                .channelDistribution(config.networkChannelDistribution)
                .channelFundingDistribution(config.networkChannelFundingDistribution)
        if (!legacyGeneratorEnabled) networkBuilder.enableRoles()
        val network = networkBuilder.build()
        // Build payments
        val cycles = if (legacyGeneratorEnabled) {
            generateRandomPaymentCycles(
                    network.nodes,
                    config.cycleCount,
                    config.paymentAmountDistribution,
                    config.paymentFlowDistribution,
                    config.paymentRegularityDistribution
            )
        } else {
            generateRoleBasedPaymentCycles(
                    agents,
                    network.nodes,
                    config.cycleCount
            )
        }

        return SimulationTemplate(agents.values.toSet(), network, cycles, config.routingAlgorithm, config.blockchainFee)
    }

    private fun buildTiny() : SimulationTemplate {
        val alice = AgentTemplate("ALICE", AgentRole.BASIC)
        val bob = AgentTemplate("BOB", AgentRole.BASIC)
        val carol = AgentTemplate("CAROL", AgentRole.BASIC)
        val agents = mapOf(alice.name to alice, bob.name to bob, carol.name to carol)

        val network = NetworkTemplateBuilder()
                .agents(agents)
                .enableLinearGraph()
                .channelFundingDistribution(UniformDistribution(1.0, 1.0))
                .wealthDistribution(UniformDistribution(3.0, 3.0))
                .build()
        val aliceNode = network.nodes.first { it.name == alice.name }
        val bobNode = network.nodes.first { it.name == bob.name }
        val carolNode = network.nodes.first { it.name == carol.name }

        return SimulationTemplate(
                agents.values.toSet(),
                network,
                listOf(
                        CycleTemplate(CycleTemplateType.PAYMENT, aliceNode.walletAddress, bobNode.walletAddress, 0.01, true),
                        CycleTemplate(CycleTemplateType.PAYMENT, bobNode.walletAddress, carolNode.walletAddress, 0.01, true),
                        CycleTemplate(CycleTemplateType.PAYMENT, aliceNode.walletAddress, carolNode.walletAddress, 0.01, true),
                        CycleTemplate(CycleTemplateType.PAYMENT, carolNode.walletAddress, aliceNode.walletAddress, 0.01, true)
                ),
                config.routingAlgorithm,
                0.001
        )
    }

    /**
     * Generate payments in [1..[cycleCount]] cycles according to the given distribution. Several parameters
     * influence the generation of payments. Especially, the [flow] of capital in the network, as well as the [regularity]
     * of payments may be controlled by setting according parameters.
     *
     * @param nodes         list of nodes that participate in transactional activities
     * @param cycleCount    the number of cycles that payments are distributed to. Payments are assigned to a cycle with
     *                      equal probability
     * @param flow          directs the flow of capital in the network, i.e. the degree of which some nodes will
     *                      act as sinks or sources of capital, respectively.
     * @param regularity    controls the regularity of payments, i.e. the ratio of reoccurring payments versus sporadic,
     *                      i.e. one-time payments.
     *
     * @return              a list of CycleTemplates, generated according to the given configuration rules
     */
    private fun generateRandomPaymentCycles(nodes: Set<NodeTemplate>, cycleCount: Int, amount: Distribution, flow: Distribution, regularity: Distribution): List<CycleTemplate> {
        /*
         * Create two lists for senders and receivers, assigning each node with a
         * certain probability of being a sending or receiving node respectively.
         */
        val list: MutableList<NodeTemplate> = nodes.toMutableList()
        val senders = list.zip(flow.weights(list.size))
        list.shuffle()
        val receivers = list.zip(flow.weights(list.size))

        val nopTemplate = CycleTemplate(CycleTemplateType.NOP)
        val cycleTemplates = (1..cycleCount).map { nopTemplate }.toMutableList()
        val random = Random()
        var size = 0
        while (size < cycleCount) {
            var x: WalletAddress?
            var y: WalletAddress?
            do {
                x = weightedRandomSelect(senders).walletAddress
                y = weightedRandomSelect(receivers).walletAddress
            } while (x == y)
            val noOfPayments = Math.min(cycleCount - size, Math.ceil(regularity.sample()).toInt())
            repeat(noOfPayments) {
                val cycleIndex = generateSequence { random.nextInt(cycleCount) }.first { i -> cycleTemplates[i].type == CycleTemplateType.NOP }
                cycleTemplates[cycleIndex] = CycleTemplate(CycleTemplateType.PAYMENT, x!!, y!!, amount.sample(), random.nextBoolean())
            }
            size += noOfPayments
        }
        return cycleTemplates
    }

    /**
     * Create payments between nodes based on the roles configuration.
     *
     * @param agents        map of agents, key is the name ot the agent.
     * @param nodes         set of node.
     * @param cycleCount    the number of cycles that payments are distributed to.
     *
     * @return              list of cycles.
     */
    private fun generateRoleBasedPaymentCycles(agents: Map<String, AgentTemplate>, nodes: Set<NodeTemplate>, cycleCount: Int): List<CycleTemplate> {
        val nodeMap = nodes.map { Pair(it.name, it) }.toMap()
        val agentContexts = agents.map {
            val node = nodeMap.getValue(it.key)
            AgentContext(
                    it.key,
                    it.value.role,
                    node,
                    node.walletBalance,
                    Math.min(Math.max(it.value.role.paymentFlowDistribution.sample(), 0.0), 1.0),
                    Math.max(it.value.role.paymentParticipationDistribution.sample(), 0.0)
            )
        }.toList()
        val totalPaymentParticipants = cycleCount * 2
        val relativePaymentParticipationSum = agentContexts.sumByDouble { it.participationProportion }
        agentContexts.forEach {
            val paymentCountTarget = totalPaymentParticipants / relativePaymentParticipationSum * it.participationProportion
            it.incomingPaymentCountTarget = it.flowProportion * paymentCountTarget
            it.outgoingPaymentCountTarget = (1.0 - it.flowProportion) * paymentCountTarget
        }

        val payments = mutableListOf<CycleTemplate>()
        var unsuccessfulRounds = 0
        do {
            unsuccessfulRounds++
            val a1 = agentContexts.random()
            val a2 = agentContexts.random()
            // Nodes are distinct
            if (a1 === a2) continue

            val amount = Math.max(a1.agent.paymentAmountDistribution.sample(), 0.001)

            // Has payer enough wealth?
            if (a1.remainingWealth() < amount) continue

            // Determine satisfaction before adding new payment
            val satisfactionBefore = a1.outgoingSatisfaction() + a2.incomingSatisfaction()

            // Create cycle template
            val c = CycleTemplate(
                    CycleTemplateType.PAYMENT,
                    a1.node.walletAddress,
                    a2.node.walletAddress,
                    amount
            )
            // Create payment context
            val p = PaymentContext(
                    a1,
                    a2,
                    amount,
                    c
            )

            a1.payments.add(p)
            a2.payments.add(p)

            // Determine satisfaction after adding new payment
            val satisfactionAfter = a1.outgoingSatisfaction() + a2.incomingSatisfaction()

            if (satisfactionAfter - satisfactionBefore <= 0.0 &&
                    (unsuccessfulRounds < cycleCount || cycleCount < payments.size + 1)) {
                // no improvement of satisfaction: remove payment
                a1.payments.remove(p)
                a2.payments.remove(p)
                continue
            }

            if (cycleCount < payments.size + 1) {
                // remove payment with lowest satisfaction
                val lsp = agentContexts
                        .mapNotNull { it.getPaymentWithLowestSatisfaction() }
                        .sortedBy { it.satisfaction() }
                        .first()
                if (lsp === p) {
                    // added payment has lowest satisfaction
                    a1.payments.remove(p)
                    a2.payments.remove(p)
                    continue
                }
                lsp.payer.payments.remove(lsp)
                lsp.receiver.payments.remove(lsp)
                payments.remove(lsp.template)
            }

            payments.add(c)
            unsuccessfulRounds = 0
        } while (unsuccessfulRounds < cycleCount * 2)

        // report nodes with low satisfaction
        for (n in agentContexts) {
            if (n.satisfaction() < 0.1) {
                log.info("${n.name}: low payment satisfaction ${"%.2f".format(n.satisfaction())} (${n.incomingPaymentCount}/${"%.2f".format(n.incomingPaymentCountTarget)}; ${n.outgoingPaymentCount}/${"%.2f".format(n.outgoingPaymentCountTarget)}) [${n.agent.name}]")
            }
        }

        return payments.shuffled()
    }

    private class AgentContext (
            val name: String,
            val agent: AgentRole,
            val node: NodeTemplate,
            val wealth: Double,
            val flowProportion: Double,
            val participationProportion: Double,
            val payments: MutableSet<PaymentContext> = mutableSetOf(),
            var incomingPaymentCountTarget: Double = 0.0,
            var outgoingPaymentCountTarget: Double = 0.0
    ) {
        val incomingPaymentCount: Int
            get() = payments.filter { it.receiver.name == name }.size
        val outgoingPaymentCount: Int
            get() = payments.filter { it.payer.name == name }.size

        fun remainingWealth() : Double = wealth + (payments.sumByDouble { it.amount * if (it.receiver.name == name) 1.0  else -1.1 }) // additional 10% for fees

        fun satisfaction() : Double = (outgoingSatisfaction() + incomingSatisfaction()) / 2

        fun getPaymentWithLowestSatisfaction() : PaymentContext? = payments.sortedBy { it.satisfaction() }.firstOrNull()

        fun incomingSatisfaction(): Double {
            val diff = Math.abs(incomingPaymentCountTarget - incomingPaymentCount)
            return if (diff > 0.5) {
                0.5 / diff
            } else {
                1.0
            }
        }

        fun outgoingSatisfaction(): Double {
            val diff = Math.abs(outgoingPaymentCountTarget - outgoingPaymentCount)
            return if (diff > 0.5) {
                0.5 / diff
            } else {
                1.0
            }
        }
    }

    private class PaymentContext (
            val payer: AgentContext,
            val receiver: AgentContext,
            val amount: Double,
            val template: CycleTemplate
    ) {
        fun satisfaction() : Double = (payer.outgoingSatisfaction() + receiver.incomingSatisfaction()) / 2
    }

    /**
     * Generates agent templates by the given relative quantity in the config parameter.
     *
     * @return map of agents, with the name of the agent as key.
     */
    private fun generateAgents(): Map<String, AgentTemplate> {
        val roles = config.agentRoles
        if (roles.isEmpty()) error("at least the definition of one role is required.")
        if (roles.any { it.value <= 0.0 }) error("relative quantity of a role must be greater then 0.")
        val size = config.networkSize
        if (size <= 0) error("the network size must be greater than 0.")

        val roleRelativeSum = roles.map { it.value }.sum()
        val roleSizes = roles.map { Pair(it.key, Math.round(it.value * size / roleRelativeSum).toInt()) }.toMutableList()
        while (roleSizes.map { it.second }.sum() < size) {
            var minRoleSize = size
            var minRoleIndex = 0
            for (i in 0 until roleSizes.size) {
                if (roleSizes[i].second < minRoleSize) {
                    minRoleSize = roleSizes[i].second
                    minRoleIndex = i
                }
            }
            roleSizes[minRoleIndex] = Pair(roleSizes[minRoleIndex].first, roleSizes[minRoleIndex].second + 1)
        }
        while (roleSizes.map { it.second }.sum() > size) {
            var maxRoleSize = 0
            var maxRoleIndex = 0
            for (i in 0 until roleSizes.size) {
                if (roleSizes[i].second > maxRoleSize) {
                    maxRoleSize = roleSizes[i].second
                    maxRoleIndex = i
                }
            }
            roleSizes[maxRoleIndex] = Pair(roleSizes[maxRoleIndex].first, roleSizes[maxRoleIndex].second - 1)
        }

        val agents = mutableMapOf<String, AgentTemplate>()
        val nameGenerator = NameGenerator()
        for (s in roleSizes) {
            for (i in 0 until s.second) {
                val name = nameGenerator.next()
                agents[name] = AgentTemplate(name, s.first)
            }
        }
        return agents
    }
}
