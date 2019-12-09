package au.csiro.data61.pcnsimulation.configuration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionsTest {

    private val beta = BetaDistribution(3.0, 5.0, 30.0)
    private val normal = NormalDistribution(10.0, 4.0)
    private val uniform = UniformDistribution(3.0)

    @Test
    fun `Draw Samples From Beta Distribution and Check Cumulative Distribution`() = runBlocking {

        val weights = beta.weights(100)

        assertEquals(100, weights.size)
        assertEquals(1.0, weights.sum(), 0.001)
    }

    @Test
    fun `Draw Samples From Normal Distribution and Check Cumulative Distribution`() = runBlocking {

        val weights = normal.weights(100)

        assertEquals(100, weights.size)
        assertEquals(1.0, weights.sum(), 0.001)
    }

    @Test
    fun `Draw Samples From Uniform Distribution and Check Cumulative Distribution`() = runBlocking {

        val weights = uniform.weights(100)

        assertEquals(100, weights.size)
        assertEquals(1.0, weights.sum(), 0.001)
    }

    @Test
    fun `Make Weighted Selection and Check Adherence to A-priori Distribution`() = runBlocking {

        val elements = listOf(Pair("a", .2), Pair("b", .6), Pair("c", .2))
        var a = 0
        var b = 0
        var c = 0

        repeat(10000) {

            when (weightedRandomSelect(elements)) {
                "a" -> a++
                "b" -> b++
                "c" -> c++
            }
        }

        val aPercent = a.toDouble() / 100.0
        val bPercent = b.toDouble() / 100.0
        val cPercent = c.toDouble() / 100.0
        assertEquals(20.0, aPercent, 2.0)
        assertEquals(60.0, bPercent, 2.0)
        assertEquals(20.0, cPercent, 2.0)
    }

    @Test
    fun `Make Weighted Selection And Check Adherence to Normal Distribution`() = runBlocking {

        val elements = List(100) { i -> i }
        val weights = NormalDistribution(5.0, 3.0).weights(elements.size)
        val count = MutableList(100) { _ -> 0 }

        val selection = elements.zip(weights)

        repeat(1000) {
            val s = weightedRandomSelect(selection)
            count[s] = count[s] + 1
        }

        println(count.filter { it == 0 }.size)
    }
}