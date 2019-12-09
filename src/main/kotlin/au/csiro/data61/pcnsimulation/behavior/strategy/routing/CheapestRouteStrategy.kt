package au.csiro.data61.pcnsimulation.behavior.strategy.routing

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.node.NoRouteFoundException
import au.csiro.data61.pcnsimulation.protocol.node.routing.Route
import au.csiro.data61.pcnsimulation.protocol.strategy.RoutingStrategy

/**
 * Basic implementation of a routing selection strategy.
 */
class CheapestRouteStrategy(
        private val onRouteSelection: (RoutingStrategy.RouteSelection) -> Unit = {}
) : RoutingStrategy {
    /**
     * Selects route candidates based on the fee, starting with the lowest.
     */
    override fun selectRoute(routes: Set<Route>, from: WalletAddress, to: WalletAddress, amount: Double): RoutingStrategy.RouteSelection {
        /*
         * Sort all candidates by fee
         */
        val sortedRoutes = routes.map { route ->
            Pair(route, route.channels
                    .dropLast(1) // No fees for payee
                    .map { it.fee }
                    .foldRight(ChannelFee.ZERO) { fee, acc -> acc.add(fee) }
                    .addToPayment(amount))
        }.sortedBy { it.second }.toMutableList()
        /*
         * Filter routes for ones with enough liquidity (splitting of payment to multiple routes is not supported).
         */
        val sortedRoutesWithEnoughLiquidity = sortedRoutes
                .filter { it.second <= it.first.channels.minBy { it.liquidity }!!.liquidity }
                .toMutableList()

        if (sortedRoutesWithEnoughLiquidity.isEmpty()) {
            throw NoRouteFoundException(from, to, amount)
        } else {
            /**
             * The first (lowest fee) route is selected (splitting of payment to multiple routes is not supported).
             */
            val route = sortedRoutesWithEnoughLiquidity[0].first
            val fee = sortedRoutesWithEnoughLiquidity[0].second - amount
            val liquidity = route.channels.minBy { it.liquidity }!!.liquidity
            val selection = RoutingStrategy.RouteSelection(
                    listOf(route),
                    liquidity,
                    fee
            )
            onRouteSelection(selection)
            return selection
        }
    }
}
