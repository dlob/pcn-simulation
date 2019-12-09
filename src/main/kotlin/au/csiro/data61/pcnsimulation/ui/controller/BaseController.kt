package au.csiro.data61.pcnsimulation.ui.controller

import au.csiro.data61.pcnsimulation.configuration.*
import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.template.SimulationTemplateBuilder
import au.csiro.data61.pcnsimulation.ui.data.Data
import au.csiro.data61.pcnsimulation.ui.data.NetworkData
import au.csiro.data61.pcnsimulation.ui.data.PaymentData
import au.csiro.data61.pcnsimulation.ui.data.getData
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import au.csiro.data61.pcnsimulation.runtime.SimulationExecutor as Ex

/**
 * Base class for API controllers
 */
abstract class BaseController {

    companion object {
        private val log by logger()
    }

    init {
        Ex.subscribe(::push)
        Ex.currentTemplate = SimulationTemplateBuilder().tiny().build()
        runBlocking {
            Ex.reset()
        }
    }

    /**
     * Initialize the controller
     */
    abstract fun start()

    /**
     * Stop the controller
     */
    abstract fun stop()

    /**
     * Callback for notifying clients about any state updates or events
     */
    abstract fun push(data: Data)

    protected suspend fun handle(cmd: String): Data? {
        return when {
            cmd.startsWith("regenerate") -> Data("regenerate", regenerate(cmd.removePrefix("regenerate ")))
            cmd.startsWith("play") -> {
                GlobalScope.launch {
                    Ex.play(1000)
                    push(Data("pause", Any()))
                }
                return Data("play", Any())
            }
            cmd.startsWith("pause") -> Data("pause", Ex.pause())
            cmd.startsWith("step") -> Data("step", Ex.step())
            cmd.startsWith("reset") -> Data("reset", Ex.reset().getData())
            cmd.startsWith("config tiny") -> Data("network", setNetwork("tiny", "{}"))
            cmd.startsWith("config small") -> Data("network", setNetwork("small", cmd.removePrefix("config small ")))
            cmd.startsWith("config medium") -> Data("network", setNetwork("medium", cmd.removePrefix("config medium ")))
            cmd.startsWith("config large") -> Data("network", setNetwork("large", cmd.removePrefix("config large ")))
            cmd.startsWith("network") -> Data("network", Ex.currentSimulation.getData())
            cmd.startsWith("payments") -> Data("payments", getPayments(cmd.removePrefix("payments ")))
            cmd.startsWith("node") -> Data("node", getNode(cmd.removePrefix("node ")) ?: Any())
            cmd.startsWith("set-balance") -> Data("set-balance", setBalance(cmd.removePrefix("set-balance ")))
            cmd.startsWith("ping") -> Data("ping", Ex.ping(cmd.removePrefix("ping ")))
            cmd.startsWith("stop") -> {
                stop()
                return Data("stop", Any())
            }
            else -> {
                log.info("Unknown command: $cmd")
                return Data("error", Any())
            }
        }
    }

    private suspend fun setNetwork(size: String, arg: String): NetworkData {
        val gson = Gson()
        val obj = gson.fromJson(arg, JsonObject::class.java)
        val rs = if (obj.has("roles")) {
            obj["roles"].asJsonObject.entrySet().map { Pair(AgentRole.valueOf(it.key), it.value.asDouble) }.toMap()
        } else {
            mapOf(Pair(AgentRole.PASSIVE_CONSUMER, 0.8), Pair(AgentRole.HUB, 0.2))
        }
        val template = when (size) {
            "small" -> SimulationTemplateBuilder().small().roles(rs).build()
            "medium" -> SimulationTemplateBuilder().medium().roles(rs).build()
            "large" -> SimulationTemplateBuilder().large().roles(rs).build()
            else -> SimulationTemplateBuilder().tiny().build()
        }
        Ex.currentTemplate = template
        return Ex.reset().getData()
    }

    private fun getPayments(arg: String): List<PaymentData> {
        val gson = Gson()
        val obj = gson.fromJson(arg, JsonObject::class.java)
        val s = if (obj.has("start")) obj.get("start").asInt else 1
        val e = if (obj.has("end")) obj.get("end").asInt else Ex.currentSimulation.cycles.size - 1
        return Ex.currentSimulation.cycles.drop(s - 1).take(e - s).getData(s)
    }

    private fun getNode(arg: String) = Ex.currentSimulation.network.nodes.values.find { it.name == arg }?.getData()

    private suspend fun regenerate(arg: String): NetworkData {
        val gson = Gson()
        val obj = gson.fromJson(arg, JsonObject::class.java)
        val w = obj["wealthDistribution"].asJsonObject
        val wealthDistribution = when {
            w.has("mean") -> NormalDistribution(w["mean"].asDouble, w["variance"].asDouble)
            w.has("alpha") -> BetaDistribution(w["alpha"].asDouble, w["beta"].asDouble, 0.0, w["scale"].asDouble)
            else -> UniformDistribution(b = w["scale"].asDouble)
        }
        val c = obj["channelDistribution"].asJsonObject
        val cf = obj["channelFundingDistribution"].asJsonObject
        val channelDistribution = UniformDistribution(b = c["scale"].asDouble)
        val channelFundingDistribution = UniformDistribution(b = cf["scale"].asDouble)
        return Ex.regenerate(SimulationConfiguration(
                networkSize = obj["size"].asInt,
                networkWealthDistribution = wealthDistribution,
                networkChannelDistribution = channelDistribution,
                networkChannelFundingDistribution = channelFundingDistribution
        )).getData()
    }

    private suspend fun setBalance(cmd: String) {
        val gson = Gson()
        val obj = gson.fromJson(cmd, JsonObject::class.java)
        Ex.setBalance(obj["fromWallet"].asString, obj["toWallet"].asString, obj["fromBalance"].asDouble)
    }
}
