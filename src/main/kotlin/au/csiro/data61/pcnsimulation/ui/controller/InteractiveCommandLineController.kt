package au.csiro.data61.pcnsimulation.ui.controller

import au.csiro.data61.pcnsimulation.ui.data.Data
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * API Controller for the command line
 */
class InteractiveCommandLineController : BaseController() {

    private var running = false

    override fun start() {
        running = true
        var cmd: String
        while (running) {
            cmd = readLine()!!
            GlobalScope.launch {
                val result = handle(cmd)
                println(result)
            }
        }
    }

    override fun stop() {
        running = false
    }

    override fun push(data: Data) {
        println(data)
    }
}