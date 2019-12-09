package au.csiro.data61.pcnsimulation.behavior.strategy

import au.csiro.data61.pcnsimulation.protocol.channel.ChannelFee
import au.csiro.data61.pcnsimulation.protocol.channel.DynamicChannelInformation
import au.csiro.data61.pcnsimulation.protocol.node.routing.Peer
import au.csiro.data61.pcnsimulation.protocol.node.routing.Route
import au.csiro.data61.pcnsimulation.behavior.strategy.routing.CheapestRouteStrategy
import au.csiro.data61.pcnsimulation.util.Crypto
import org.junit.Assert.assertEquals
import org.junit.Test


class CheapestRouteStrategyTest {

    private val routingStrategy = CheapestRouteStrategy()

    @Test(expected = Exception::class)
    fun `Throw Exception on empty Route Set`() {
        routingStrategy.selectRoute(emptySet(),"w1", "w2", 5.0)
    }

    @Test(expected = Exception::class)
    fun `Throw Exception when no Route with Sufficient Liquidity is Found`() {
        routingStrategy.selectRoute(setOf(Route(
                listOf(
                        DynamicChannelInformation("w1", "w2", 4.0, ChannelFee.ZERO),
                        DynamicChannelInformation("w2", "w3", 3.0, ChannelFee.ZERO)
                ),
                mapOf(
                        Pair("w1", Peer("w1", Crypto.generateKeyPair().public, "w1", "ip1")),
                        Pair("w2", Peer("w2", Crypto.generateKeyPair().public, "w2", "ip2")),
                        Pair("w3", Peer("w3", Crypto.generateKeyPair().public, "w3", "ip3"))
                )
        )), "w1", "w3",5.0)
    }

    @Test(expected = Exception::class)
    fun `Throw Exception when no Route with Sufficient Liquidity is Found Because of Fee`() {
        routingStrategy.selectRoute(setOf(Route(
                listOf(
                        DynamicChannelInformation("w1", "w2", 6.0, ChannelFee(1.0, 2.0)),
                        DynamicChannelInformation("w2", "w3", 6.0, ChannelFee.ZERO)
                ),
                mapOf(
                        Pair("w1", Peer("w1", Crypto.generateKeyPair().public, "w1", "ip1")),
                        Pair("w2", Peer("w2", Crypto.generateKeyPair().public, "w2", "ip2")),
                        Pair("w3", Peer("w3", Crypto.generateKeyPair().public, "w3", "ip3"))
                )
        )), "w1", "w3", 5.0)
    }

    @Test
    fun `Route with Lowest Fees is Selected`() {
        val peers = mapOf(
                Pair("w1", Peer("w1", Crypto.generateKeyPair().public, "w1", "ip1")),
                Pair("w2", Peer("w2", Crypto.generateKeyPair().public, "w2", "ip2")),
                Pair("w3", Peer("w3", Crypto.generateKeyPair().public, "w3", "ip3")),
                Pair("w4", Peer("w4", Crypto.generateKeyPair().public, "w4", "ip4")),
                Pair("w5", Peer("w5", Crypto.generateKeyPair().public, "w5", "ip5"))
        )
        val routes = listOf(
                Route(
                        listOf(
                                DynamicChannelInformation("w1", "w2", 10.0, ChannelFee(1.1, 0.0)),
                                DynamicChannelInformation("w2", "w3", 10.0, ChannelFee.ZERO)
                        ), peers.filter { setOf("w1", "w2", "w3").contains(it.key) }),
                Route(
                        listOf(
                                DynamicChannelInformation("w1", "w4", 10.0, ChannelFee(1.0, 0.5)),
                                DynamicChannelInformation("w4", "w3", 10.0, ChannelFee.ZERO)
                        ), peers.filter { setOf("w1", "w4", "w3").contains(it.key) }),
                Route(
                        listOf(
                                DynamicChannelInformation("w1", "w5", 10.0, ChannelFee(1.01, 0.1)),
                                DynamicChannelInformation("w5", "w3", 10.0, ChannelFee.ZERO)
                        ), peers.filter { setOf("w1", "w5", "w3").contains(it.key) })
        )

        val selectedRoute = routingStrategy.selectRoute(routes.toSet(), "w1", "w3", 5.0)

        assertEquals(1, selectedRoute.routes.size)
        assertEquals(routes.component3(), selectedRoute.routes.component1())

        assertEquals(10.0, selectedRoute.maxFlow, 0.001)
        assertEquals(0.15, selectedRoute.overallFees, 0.001)
    }
}
