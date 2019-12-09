package au.csiro.data61.pcnsimulation.protocol.channel

/**
 * Represents the fee a sender has to pay an intermediate node
 *
 * The fee is composed of a [rate] r ∈ [0, ∞) and a [fixed] amount f ∈ (-∞, ∞).
 * To calculate the final amount y for a payment x, following linear function is used: y = r * x + f.
 */
data class ChannelFee (
        val rate: Double,
        val fixed: Double
) {
    companion object {
        val ZERO = ChannelFee(1.0, 0.0)
    }

    /**
     * Adds up fees
     *
     * Adding up fees is not commutative. It always needs to be done in the order of nodes on the path,
     * started with the last one.
     */
    fun add(fee: ChannelFee): ChannelFee {
        return ChannelFee(rate * fee.rate, fixed * fee.rate + fee.fixed)
    }

    /**
     * Calculate the amount to be transferred, in order that the payment amount arrives at the destination.
     */
    fun addToPayment(paymentAmount: Double): Double {
        return paymentAmount * rate + fixed
    }

    /**
     * Calculate the amount remaining after removing the fee.
     */
    fun removeFromPayment(paymentAmount: Double): Double {
        return (paymentAmount - fixed) / rate
    }
}
