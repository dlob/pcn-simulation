package au.csiro.data61.pcnsimulation.behavior.cycle

/**
 * Simulation cycle that does nothing.
 */
class NopCycle : Cycle {
    override suspend fun run() {
        // Do nothing
    }
}
