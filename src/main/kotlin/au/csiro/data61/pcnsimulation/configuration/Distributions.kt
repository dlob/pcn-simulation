package au.csiro.data61.pcnsimulation.configuration

import org.apache.commons.math3.random.RandomGenerator
import org.apache.commons.math3.random.Well19937c
import java.text.ParseException
import java.util.concurrent.ThreadLocalRandom

/**
 * Interface for [probability distributions](https://en.wikipedia.org/wiki/Probability_distribution).
 */
interface Distribution {

    /**
     * Assign weights to [size] bars in a histogram according to the distribution.
     */
    fun weights(size: Int): List<Double>

    /**
     * Draw a random sample from the distribution.
     */
    fun sample(): Double

    /**
     * Return the expected value (i.e. mean) of the distribution.
     */
    fun expectedValue(): Double
}

/**
 * Select an element out of a list of elements randomly, where the probability of being picked
 * is determined by the weight assigned to the element.
 */
fun <T> weightedRandomSelect(elements: List<Pair<T, Double>>): T {
    val sum = elements.sumByDouble { it.second }
    var r = ThreadLocalRandom.current().nextDouble(0.0, sum)
    for ((elem, w) in elements) {
        r -= w
        if (r <= 0.0) {
            return elem
        }
    }
    error("weightedRandomSelect(): no element selected.")
}

/**
 * The [Beta Distribution](https://en.wikipedia.org/wiki/Beta_distribution) is a finite-interval distribution
 * and is commonly used in many disciplines.
 */
data class BetaDistribution(val alpha: Double = 2.0, val beta: Double = 5.0, val a: Double = 0.0, val b: Double = 1.0, val rng: RandomGenerator = Well19937c()) : Distribution {
    val distribution = org.apache.commons.math3.distribution.BetaDistribution(rng, alpha, beta)

    override fun sample() = (b - a) * distribution.sample() + a

    override fun expectedValue() = (b - a) / (1.0 + (beta / alpha)) + a

    override fun weights(size: Int): List<Double> {
        val len = (1.0 / size.toDouble())
        return (0..(size - 1)).map { distribution.probability(len * it, len * (it + 1)) }
    }
}

/**
 * The standard [Normal Distribution](https://en.wikipedia.org/wiki/Normal_distribution) with non-negative values.
 */
data class NormalDistribution(val mean: Double = 0.0, val sd: Double = 1.0, val rng: RandomGenerator = Well19937c()) : Distribution {
    val distribution = org.apache.commons.math3.distribution.NormalDistribution(rng, mean, sd)

    override fun sample() = Math.max(0.01, distribution.sample())

    override fun expectedValue() = mean

    override fun weights(size: Int): List<Double> {
        val len = (sd * 6.0 / size.toDouble())
        val diff = 0.002699796063260096 / size
        return ((-size / 2)..(size / 2 - 1)).map { distribution.probability((len * it) + mean, (len * (it + 1)) + mean) + diff }
    }
}

/**
 * The standard [Uniform Distribution](https://en.wikipedia.org/wiki/Uniform_distribution_(continuous)).
 */
data class UniformDistribution(val a: Double = 0.0, val b: Double = 1.0, val rng: RandomGenerator = Well19937c()) : Distribution {
    constructor(b: Double = 1.0) : this(0.0, b)

    override fun sample() = a + rng.nextDouble() * (b - a)

    override fun expectedValue() = (a + b) / 2

    override fun weights(size: Int) = List(size) { _ -> 1.0 / size }
}

fun parseDistribution(str: String) : Distribution {
    val parts = str.split('(', ')', ',')
    val dist = parts[0].trim()
    if (dist == "BetaDistribution") {
        return BetaDistribution(parts[1].toDouble(), parts[2].toDouble(), parts.elementAtOrElse(3) { "0.0" }.toDouble(), parts.elementAtOrElse(4) { "1.0" }.toDouble())
    } else if (dist == "NormalDistribution") {
        return NormalDistribution(parts[1].toDouble(), parts[2].toDouble())
    } else if (dist == "UniformDistribution") {
        return UniformDistribution(parts[1].toDouble(), parts[2].toDouble())
    } else {
        throw ParseException("Distribution not found: $str", 0)
    }
}

fun Distribution.copy(seed: Long? = null): Distribution {
    val rng = if (seed == null) Well19937c() else Well19937c(seed)
    return when (this) {
        is BetaDistribution -> BetaDistribution(this.alpha, this.beta, this.a, this.b, rng)
        is NormalDistribution -> NormalDistribution(this.mean, this.sd, rng)
        is UniformDistribution -> UniformDistribution(this.a, this.b, rng)
        else -> error("Distribution cannot be copied.")
    }
}
