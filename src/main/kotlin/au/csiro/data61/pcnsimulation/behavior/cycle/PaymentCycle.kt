package au.csiro.data61.pcnsimulation.behavior.cycle

import au.csiro.data61.pcnsimulation.WalletAddress
import au.csiro.data61.pcnsimulation.behavior.agent.Agent

/**
 * Simulation cycle that makes a single payment.
 */
class PaymentCycle (
        /**
         * Represents the source wallet.
         */
        private val fromAgent: Agent,

        /**
         * Represents the source wallet.
         */
        val from: WalletAddress,

        /**
         * Represents the destination wallet.
         */
        val to: WalletAddress,

        /**
         * Represents the payment amount.
         */
        val amount: Double,

        /**
         * Indicates if the payment is urgent.
         */
        val urgent: Boolean = false
) : Cycle {

    override suspend fun run() {
        fromAgent.addPayment(from, to, amount, urgent)
    }
}
