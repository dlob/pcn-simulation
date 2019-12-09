package au.csiro.data61.pcnsimulation

import au.csiro.data61.pcnsimulation.runtime.SimulationExecutor
import au.csiro.data61.pcnsimulation.template.SimulationTemplateBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class SimulationTest {

    @Test
    fun `Generate Simulation and Check Number of Nodes`() = runBlocking {
        val mediumTemplate = SimulationTemplateBuilder().medium().build()
        SimulationExecutor.currentTemplate = mediumTemplate
        SimulationExecutor.reset()
        SimulationExecutor.play()

        for (wallet in SimulationExecutor.currentSimulation.network.nodes.keys) {
            Assert.assertTrue(SimulationExecutor.currentSimulation.network.nodes.values.first().blockchain.balance(wallet)!! >= 0)
        }

        Assert.assertEquals(0, SimulationExecutor.currentSimulation.network.channels.filter { it.fromBalance() <= 0 || it.toBalance() <= 0 }.size)
    }
}
