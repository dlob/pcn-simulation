package au.csiro.data61.pcnsimulation.template

import au.csiro.data61.pcnsimulation.WalletAddress

/**
 * Template for each simulation cycle.
 * Each cycle has a type:
 * - NOP ... No operation in this cycle
 * - PAYMENT ... A payment [from] one wallet [to] another wallet with a payment [amount] is issued.
 */
data class CycleTemplate(
        /**
         * Type of the cycle.
         */
        val type: CycleTemplateType,

        /**
         * If the type of the cycle is [PAYMENT]: represents the source wallet.
         */
        val from: WalletAddress? = null,

        /**
         * If the type of the cycle is [PAYMENT]: represents the destination wallet.
         */
        val to: WalletAddress? = null,

        /**
         * If the type of the cycle is [PAYMENT]: represents the payment amount.
         */
        val amount: Double? = null,

        /**
         * If the type of the cycle is [PAYMENT]: indicates if the payment is urgent.
         */
        val urgent: Boolean? = false
)

enum class CycleTemplateType {
    NOP,
    PAYMENT
}
