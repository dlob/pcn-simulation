package au.csiro.data61.pcnsimulation.ui.controller

import au.csiro.data61.pcnsimulation.logger
import au.csiro.data61.pcnsimulation.ui.data.Data
import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.ws
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.websocket.api.Session
import kotlin.concurrent.fixedRateTimer

/**
 * API Controller for WebSockets
 */
class WebServiceController(port: Int)
    : BaseController() {

    companion object {
        private val log by logger()
    }

    private var app: Javalin = Javalin.create {config ->
        config.enableCorsForOrigin("http://127.0.0.1:$port", "http://localhost:$port")
        config.addStaticFiles("/public")
    }.routes {
        ws("websocket") { ws ->
            ws.onConnect { ctx ->
                webSocketSession = ctx.session
                log.info("Opened $webSocketSession")
            }
            ws.onMessage { ctx ->
                log.info("Received: ${ctx.message()}")
                GlobalScope.launch {
                    val result = handle(ctx.message())
                    if (result != null) {
                        channel.send(result)
                    }
                }
            }
            ws.onClose { ctx -> log.info("Closed ${ctx.session}, ${ctx.status()}: ${ctx.reason()}") }
            ws.onError { ctx -> log.info("Error: ${ctx.session}, ${ctx.error()}") }
        }
    }.start(port)

    private var webSocketSession: Session? = null
    private val channel = Channel<Data>(100)

    override fun start() {
        GlobalScope.launch {
            val gson = Gson()
            while (true) {
                val data = channel.receive()
                webSocketSession?.remote?.sendString(gson.toJson(data))
            }
        }
        fixedRateTimer("ws-keep-alive", true, 60000, 60000) {
            push(Data("keep-alive", Any()))
        }
    }

    override fun push(data: Data) = runBlocking {
        channel.send(data)
    }

    override fun stop() {
        app.stop()
    }
}