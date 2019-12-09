package au.csiro.data61.pcnsimulation.runtime

import au.csiro.data61.pcnsimulation.IPAddress
import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.cycle.Cycle
import au.csiro.data61.pcnsimulation.configuration.SimulationConfiguration
import au.csiro.data61.pcnsimulation.environment.Simulation
import au.csiro.data61.pcnsimulation.environment.SimulationBuilder
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.protocol.communication.AsyncIPNetwork
import au.csiro.data61.pcnsimulation.protocol.communication.IPNetwork
import au.csiro.data61.pcnsimulation.protocol.communication.IPPacket
import au.csiro.data61.pcnsimulation.protocol.communication.SyncIPNetwork
import au.csiro.data61.pcnsimulation.protocol.message.request.PingRequest
import au.csiro.data61.pcnsimulation.template.SimulationTemplate
import au.csiro.data61.pcnsimulation.template.SimulationTemplateBuilder
import au.csiro.data61.pcnsimulation.ui.data.Data
import jdk.nashorn.internal.ir.debug.ObjectSizeCalculator
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log2

/**
 * Execute simulations interactively, including starting, pausing, and stepping through cycles.
 */
object SimulationExecutor {

    var cycleNumber = 0

    lateinit var currentTemplate: SimulationTemplate
    lateinit var currentSimulation: Simulation
    lateinit var currentCycle: Cycle

    private var agentJobs = listOf<Job>()
    private var nodeJobs = listOf<Job>()
    private var autoPlay = false
    private val subscribers = mutableListOf<(Data) -> Unit>()

    private val log by logger()

    /**
     * Add a callback function for receiving push-notifications
     */
    fun subscribe(function: (Data) -> Unit) {
        subscribers.add(function)
    }

    /**
     * Regenerate current simulation with a given configuration
     */
    suspend fun regenerate(config: SimulationConfiguration): Simulation {
        currentTemplate = SimulationTemplateBuilder()
                .config(config)
                .build()
        return reset()
    }

    /**
     * Reset the current simulation
     */
    suspend fun reset(): Simulation {
        if (this::currentSimulation.isInitialized) stop()
        cycleNumber = 0
        val ipNetwork: IPNetwork = SyncIPNetwork()
        ipNetwork.subscribe(::networkStats)
        if (ipNetwork is AsyncIPNetwork) ipNetwork.start()
        currentSimulation = SimulationBuilder()
                .template(currentTemplate)
                .communication(ipNetwork)
                .build()
        start()
        return currentSimulation
    }

    private suspend fun start() {
        for (node in currentSimulation.network.nodes.values) {
            for (subscriber in subscribers) {
                node.subscribe(subscriber)
            }
        }
        // Ramp-up phase: cycle nodes to initialize
        val rampUpCycleCount = (log2(currentSimulation.network.nodes.size.toDouble()) * 10).toInt()
        log.info("Ramp-up phase: ${rampUpCycleCount + 1} cycles")
        for (cn in -rampUpCycleCount..0) {
            currentSimulation.network.nodes.values.forEach { it.cycle(cn) }
        }
        // Resetting network packet counter
        nodePacketCount.set(0)
        nodePacketSize.set(0)
        routerPacketCount.set(0)
        routerPacketSize.set(0)
        log.info("Starting Simulation")
    }

    private suspend fun stop() {
        log.info("Stopping Simulation")
        nodeJobs.forEach { it.cancelAndJoin() }
        agentJobs.forEach { it.cancelAndJoin() }
        val ipNetwork = currentSimulation.communication
        if (ipNetwork is AsyncIPNetwork) ipNetwork.stop()
        for (node in currentSimulation.network.nodes.values) {
            node.clearSubscribers()
        }
    }

    /**
     * Run one cycleNumber after another periodically.
     */
    suspend fun play(delayMillis: Long = 0) {
        autoPlay = true
        while (autoPlay) {
            step()
            if (delayMillis > 0)
                delay(delayMillis)
        }
    }

    /**
     * Finish the current cycleNumber and pause afterwards
     */
    fun pause() {
        autoPlay = false
    }

    /**
     * Run the next simulation cycleNumber
     */
    suspend fun step() = coroutineScope {
        val ipNetwork = currentSimulation.communication
        cycleNumber++

        if (cycleNumber <= currentSimulation.cycles.size) {
            push(Data("cycle", cycleNumber))
            currentCycle = currentSimulation.cycles[cycleNumber - 1]
            currentCycle.run()

            currentSimulation.agents.forEach { it.cycle(cycleNumber) }

            currentSimulation.network.nodes.values.forEach { it.cycle(cycleNumber) }

            if (ipNetwork is AsyncIPNetwork) ipNetwork.waitForCallbacks()
            push(Data("node-network-usage", Pair(nodePacketCount.getAndSet(0), nodePacketSize.getAndSet(0))))
            push(Data("router-network-usage", Pair(routerPacketCount.getAndSet(0), routerPacketSize.getAndSet(0))))
        } else {
            autoPlay = false
            log.info("Simulation finished")
        }
    }

    private fun push(data: Data) {
        for (subscriber in subscribers) {
            subscriber(data)
        }
    }

    suspend fun ping(ipAddress: IPAddress) {
        val socket = currentSimulation.communication.createSocket("255.255.255.255")
        socket.connect()
        try {
            socket.request(ipAddress, PingRequest(), 100)
            push(Data("ping-success", "Received response from $ipAddress"))
        } catch (ex: TimeoutCancellationException) {
            push(Data("ping-failed", "$ipAddress didn't respond"))
        }
        socket.close()
    }

    suspend fun setBalance(from: WalletAddress, to: WalletAddress, fromBalance: Double) {
        val fromNode = currentSimulation.network.nodes.getValue(from)
        val channel = fromNode.channels.find { it.fromWallet == from && it.toWallet == to }!!
        val diff = channel.fromBalance() - fromBalance
        fromNode.makeChannelTransaction(to, diff)
    }

    private var nodePacketCount = AtomicInteger()
    private var nodePacketSize = AtomicLong()
    private var routerPacketCount = AtomicInteger()
    private var routerPacketSize = AtomicLong()

    private fun networkStats(packet: IPPacket) {
        if (packet.payload.javaClass.`package`.name.contains("protocol.message")) {
            nodePacketCount.incrementAndGet()
            nodePacketSize.addAndGet(ObjectSizeCalculator.getObjectSize(packet.payload))
        } else {
            routerPacketCount.incrementAndGet()
            routerPacketSize.addAndGet(ObjectSizeCalculator.getObjectSize(packet.payload))
        }
    }
}
