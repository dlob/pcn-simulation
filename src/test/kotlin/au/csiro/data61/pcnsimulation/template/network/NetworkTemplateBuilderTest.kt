package au.csiro.data61.pcnsimulation.template.network

import au.csiro.data61.pcnsimulation.configuration.BetaDistribution
import au.csiro.data61.pcnsimulation.configuration.UniformDistribution
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test


class NetworkTemplateBuilderTest {

    private val networkSize = 100
    private val wealthDistribution = BetaDistribution(2.0, 6.0)
    private val channelDistribution = UniformDistribution(4.0)
    private val fundingDistribution = UniformDistribution(.001)

    private val template = NetworkTemplateBuilder()
            .size(networkSize)
            .wealthDistribution(wealthDistribution)
            .channelDistribution(channelDistribution)
            .channelFundingDistribution(fundingDistribution)
            .build()

    @Test
    fun `Generate Template and Check Wallet Addresses All Different`() = runBlocking {
        Assert.assertEquals(template.nodes.size, template.nodes.map { it.walletAddress }.toSet().size)
    }

    @Test
    fun `Generate Template and Check IP Addresses All Different`() = runBlocking {
        Assert.assertEquals(template.nodes.size, template.nodes.map { it.ipAddress }.toSet().size)
    }

    @Test
    fun `Generate Template and Check Number of Nodes`() = runBlocking {
        Assert.assertEquals(networkSize, template.nodes.size)
    }

    @Test
    fun `Generate Template and Check Wealth Distribution`() = runBlocking {
        val expectedWealth = wealthDistribution.expectedValue()
        Assert.assertEquals(expectedWealth, template.nodes.map { it.walletBalance }.average(), 0.1)
    }

    @Test
    fun `Generate Template and Check Channel Distribution`() = runBlocking {
        val expectedNoOfChannels = channelDistribution.expectedValue() * template.nodes.size
        Assert.assertEquals(expectedNoOfChannels, template.channels.size.toDouble(), 15.0)
    }

    @Test
    fun `Generate Template and Check Funding Distribution`() = runBlocking {
        val expectedFunding = fundingDistribution.expectedValue()
        Assert.assertEquals(expectedFunding * 2, template.channels.map { it.fromBalance + it.toBalance }.average(), 0.1)
    }
}
