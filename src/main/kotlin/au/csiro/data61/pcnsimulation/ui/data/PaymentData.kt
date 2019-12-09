package au.csiro.data61.pcnsimulation.ui.data

import au.csiro.data61.pcnsimulation.WalletAddress

data class PaymentData(
        val cycle: Int,
        val from: WalletAddress,
        val to: WalletAddress,
        val amount: Double
)
