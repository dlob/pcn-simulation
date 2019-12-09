package au.csiro.data61.pcnsimulation

import au.csiro.data61.pcnsimulation.protocol.blockchain.GenericBlockchainTest
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionChannelTest
import au.csiro.data61.pcnsimulation.protocol.channel.TransactionTest
import au.csiro.data61.pcnsimulation.protocol.communication.AsyncIPNetworkTest
import au.csiro.data61.pcnsimulation.protocol.node.BasicNodeTest
import au.csiro.data61.pcnsimulation.protocol.node.routing.BasicRouterTest
import au.csiro.data61.pcnsimulation.template.SimulationTemplateBuilderTest
import au.csiro.data61.pcnsimulation.configuration.DistributionsTest
import au.csiro.data61.pcnsimulation.environment.SimulationBuilderTest
import au.csiro.data61.pcnsimulation.template.network.NetworkTemplateBuilderTest
import au.csiro.data61.pcnsimulation.behavior.strategy.CheapestRouteStrategyTest
import au.csiro.data61.pcnsimulation.protocol.node.routing.MDARTRouterTest
import au.csiro.data61.pcnsimulation.util.CryptoTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        CryptoTest::class,
        BasicNodeTest::class,
        BasicRouterTest::class,
        MDARTRouterTest::class,
        TransactionTest::class,
        CheapestRouteStrategyTest::class,
        DistributionsTest::class,
        SimulationBuilderTest::class,
        SimulationTemplateBuilderTest::class,
        NetworkTemplateBuilderTest::class,
        GenericBlockchainTest::class,
        TransactionChannelTest::class,
        AsyncIPNetworkTest::class
)
class TestSuite
