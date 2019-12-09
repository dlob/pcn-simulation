package au.csiro.data61.pcnsimulation.behavior.cycle

/**
 * Represents a simulation cycle, e.g. single payment.
 */
interface Cycle {
    /**
     * Execute all operations belonging to this simulation cycle.
     */
    suspend fun run()
}
