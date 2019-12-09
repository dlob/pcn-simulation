package au.csiro.data61.pcnsimulation.ui

import au.csiro.data61.pcnsimulation.configuration.AgentRole
import au.csiro.data61.pcnsimulation.configuration.RoutingAlgorithm
import au.csiro.data61.pcnsimulation.configuration.SimulationConfiguration
import au.csiro.data61.pcnsimulation.configuration.parseDistribution
import au.csiro.data61.pcnsimulation.runtime.SimulationExecutor
import au.csiro.data61.pcnsimulation.template.SimulationTemplate
import au.csiro.data61.pcnsimulation.template.SimulationTemplateBuilder
import au.csiro.data61.pcnsimulation.ui.controller.InteractiveCommandLineController
import au.csiro.data61.pcnsimulation.ui.controller.WebServiceController
import au.csiro.data61.pcnsimulation.util.Crypto
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.*
import java.io.File
import java.lang.reflect.Type
import java.security.KeyPair
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Command(name = "pcn-simulation", mixinStandardHelpOptions = true)
object PCNSimulationCLI : Runnable {

    override fun run() {
        usage(this, System.out)
    }

    @Command(mixinStandardHelpOptions = true, description = ["Execute simulations in a web gui."])
    fun web(@Option(names = ["-p", "--port"], defaultValue = "8081", paramLabel = "<port_number>", description = ["Port of HTTP-server."]) port: Int) {
        WebServiceController(port).start()
    }

    @Command(mixinStandardHelpOptions = true, description = ["Execute simulations in an interactive shell."])
    fun interactive() {
        InteractiveCommandLineController().start()
    }

    @Command(mixinStandardHelpOptions = true, description = ["Creates a new template and saves it in <FILE>."])
    fun template(
            @Mixin options: PCNSimulationOptions,
            @Option(names = ["--pretty"], description = ["Pretty print JSON output."]) prettyPrint: Boolean,
            @Parameters(arity = "1", paramLabel = "<FILE>", description = ["New template file."]) file: File
    ) {
        val config = options.getConfiguration()
        val template = SimulationTemplateBuilder()
                .config(config)
                .build()
        val gsonBuilder = GsonBuilder()
        if (prettyPrint) {
            gsonBuilder.setPrettyPrinting()
        }
        gsonBuilder.registerTypeAdapter(KeyPair::class.java, KeyPairTypeAdapter)
        val gson = gsonBuilder.create()
        file.writer().use {
            gson.toJson(template, SimulationTemplate::class.java, it)
        }
        println("Template written to '${file.absolutePath}'.")
    }

    @Command(mixinStandardHelpOptions = true, description = ["Executes a simulation."])
    fun simulate(
            @Mixin options: PCNSimulationOptions,
            @Option(names = ["-t", "--template"], paramLabel = "<template_file>", description = ["Load template file."]) templateFile: File?,
            @Option(names = ["--tiny"], description = ["Use tiny template."]) tiny: Boolean,
            @Option(names = ["--small"], description = ["Use small template."]) small: Boolean,
            @Option(names = ["--medium"], description = ["Use medium template."]) medium: Boolean,
            @Option(names = ["--large"], description = ["Use tiny template."]) large: Boolean
    ) {
        if (templateFile != null) {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(KeyPair::class.java, KeyPairTypeAdapter)
            val gson = gsonBuilder.create()
            templateFile.reader().use {
                var template = gson.fromJson(it, SimulationTemplate::class.java)
                if (options.routingAlgorithm != null)
                    template = template.copy(routingAlgorithm = options.routingAlgorithm!!)
                if (options.blockchainFee != null)
                    template = template.copy(blockchainFee = options.blockchainFee!!)
                SimulationExecutor.currentTemplate = template
            }
        } else {
            val builder = SimulationTemplateBuilder()
            val config = options.getConfiguration()
            builder.config(config)
            when {
                tiny -> builder.tiny()
                small -> builder.small()
                medium -> builder.medium()
                large -> builder.large()
            }
            SimulationExecutor.currentTemplate = builder.build()
        }
        SimulationExecutor.subscribe(::println)

        var payments = 0
        var paymentsSuccess = 0
        SimulationExecutor.subscribe { d ->
            when(d.topic) {
                "single-payment" -> payments++
                "multi-payment" -> payments++
                "single-payment-successful" -> paymentsSuccess++
                "multi-payment-successful" -> paymentsSuccess ++
            }
        }
        runBlocking {
            SimulationExecutor.reset()
            SimulationExecutor.play()
        }
        println("RESULTS: $paymentsSuccess/$payments = ${(paymentsSuccess.toDouble() / payments.toDouble() * 100.0).roundToInt()}%")
    }

    class PCNSimulationOptions {
        private var simulationConfiguration = SimulationConfiguration()

        fun getConfiguration() = simulationConfiguration

        @set:Option(names = ["-c", "--cycles"], paramLabel = "<number_of_cycles>", description = ["Number of cycles to be executed."])
        var cycles by option<Int> { s, v -> s.copy(cycleCount = v)}

        @set:Option(names = ["-s", "--size"], paramLabel = "<network_size>", description = ["Number of nodes in the network."])
        var size by option<Int> { s, v -> s.copy(networkSize = v)}

        @set:Option(names = ["-a", "--agents"], paramLabel = "<agent_roles>", description = ["Agent roles and distribution to be used."])
        var agents by option<Map<AgentRole, Double>> { s, v -> s.copy(agentRoles = v)}

        @set:Option(names = ["--routing"], paramLabel = "<routing_algorithm>", description = ["Routing algorithm"])
        var routingAlgorithm by option<RoutingAlgorithm> { s, v -> s.copy(routingAlgorithm = v)}

        @set:Option(names = ["--bcfee"], paramLabel = "<blockchain_fee>", description = ["Blockchain fee"])
        var blockchainFee by option<Double> { s, v -> s.copy(blockchainFee = v)}

        @set:Option(names = ["--wealth"], paramLabel = "<wealth_distribution>", description = ["Distribution of wealth."])
        var wealthDistribution by option<String> { s, v -> s.copy(networkWealthDistribution = parseDistribution(v))}

        @set:Option(names = ["--channels"], paramLabel = "<channel_distribution>", description = ["Distribution of channels."])
        var channelDistribution by option<String> { s, v -> s.copy(networkChannelDistribution = parseDistribution(v))}

        @set:Option(names = ["--channelFunding"], paramLabel = "<channel_funding_distribution>", description = ["Distribution of capital that is locked in channels."])
        var channelFundingDistribution by option<String> { s, v -> s.copy(networkChannelFundingDistribution = parseDistribution(v))}

        fun <T : Any?> option(setter: (SimulationConfiguration, T) -> SimulationConfiguration) = object : ReadWriteProperty<Any, T?> {
            private var field: T? = null

            override fun getValue(thisRef: Any, property: KProperty<*>): T? = field

            override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
                field = value
                if (value != null)
                    simulationConfiguration = setter(simulationConfiguration, value)
            }
        }
    }

    object KeyPairTypeAdapter: JsonSerializer<KeyPair>, JsonDeserializer<KeyPair> {
        override fun serialize(src: KeyPair?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            val keyPairObj = JsonObject()
            keyPairObj.addProperty("privateKey", Crypto.encodeKey(src!!.private))
            keyPairObj.addProperty("publicKey", Crypto.encodeKey(src.public))
            return keyPairObj
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): KeyPair {
            val keyPairObj = json!!.asJsonObject
            val privateKeyEncoded = keyPairObj.getAsJsonPrimitive("privateKey").asString
            val publicKeyEncoded = keyPairObj.getAsJsonPrimitive("publicKey").asString
            return KeyPair(Crypto.decodePublicKey(publicKeyEncoded), Crypto.decodePrivateKey(privateKeyEncoded))
        }
    }
}
