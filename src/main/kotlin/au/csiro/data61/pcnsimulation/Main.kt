package au.csiro.data61.pcnsimulation

import au.csiro.data61.pcnsimulation.ui.PCNSimulationCLI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

typealias WalletAddress = String
typealias IPAddress = String
typealias Hash = String

/**
 * Provides a logger as property delegate.
 * Usage in classes:
 * companion object {
 *     private val log by logger()
 * }
 * Singletons can use the property delegate without the companion object.
 */
fun <R : Any> R.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(this.javaClass.takeUnless {
        it.name.endsWith("\$Companion")
    } ?: this.javaClass.enclosingClass) }
}

fun main(args: Array<String>) {
    CommandLine(PCNSimulationCLI).execute(*args)
}
