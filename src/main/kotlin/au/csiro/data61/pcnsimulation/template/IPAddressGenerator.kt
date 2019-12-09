package au.csiro.data61.pcnsimulation.template

import au.csiro.data61.pcnsimulation.IPAddress

/**
 * Generates distinct communication addresses.
 */
class IPAddressGenerator {

    private var counter = 0

    fun next(): IPAddress {
        counter++
        return "10.0.0.$counter"
    }
}
