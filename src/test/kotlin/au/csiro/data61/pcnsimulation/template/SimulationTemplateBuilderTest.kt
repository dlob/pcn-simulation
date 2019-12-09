package au.csiro.data61.pcnsimulation.template

import au.csiro.data61.pcnsimulation.configuration.SimulationConfiguration
import au.csiro.data61.pcnsimulation.configuration.NormalDistribution
import au.csiro.data61.pcnsimulation.configuration.UniformDistribution
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationTemplateBuilderTest {

    private val paymentConfig = SimulationConfiguration(
            cycleCount = 100,
            networkSize = 3,
            paymentAmountDistribution = UniformDistribution(0.01),
            paymentRegularityDistribution = UniformDistribution(2.0),
            paymentFlowDistribution = NormalDistribution(7.0, 3.0)
    )

    @Test
    fun `Generate Payments and Check Number of Generated Payments`() = runBlocking {
        val cycles = SimulationTemplateBuilder().config(paymentConfig).build().cycles
        assertEquals(100, cycles.size)
        assertEquals(100, cycles.filter { it.type == CycleTemplateType.PAYMENT }.size)
    }

    @Test
    fun `Generate Payments and Check Payment Amounts`() = runBlocking {
        val cycles = SimulationTemplateBuilder().config(paymentConfig).build().cycles
        val averageAmount = cycles.map { it.amount!! }.average()
        /*
         * Since the amount follows a distribution, the average payment should equal the expected value of the distribution
         */
        assertEquals(paymentConfig.paymentAmountDistribution.expectedValue(), averageAmount, 0.01)
    }
}
