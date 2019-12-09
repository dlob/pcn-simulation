package au.csiro.data61.pcnsimulation.protocol.strategy

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.node.routing.Route

/**
 * Determines how routes are found, based on the provided (local) network information. The implementation of the
 * strategy might be capable of splitting payments to separate routes.
 */
interface RoutingStrategy {

    data class RouteSelection(
            val routes: List<Route>,
            val maxFlow: Double,
            val overallFees: Double
    )

    /**
     * Rank and select routing options based on dynamic, short-lived channel information
     */
    fun selectRoute(routes: Set<Route>, from: WalletAddress, to: WalletAddress, amount: Double): RouteSelection
}
