package au.csiro.data61.pcnsimulation.protocol.strategy

/**
 * A strategy is a collection procedures that determine protocol-level decisions. The different sub-strategies are
 * modularized into four unrelated concerns, as they do not influence each other and may be exchanged for other strategies.
 */
class Strategy {
    /**
     * Determines how a node handles its channels on protocol level.
     */
    lateinit var channel: ChannelStrategy

    /**
     * Determines how much information a node discloses to its peers on protocol level.
     */
    lateinit var information: DisclosureStrategy

    /**
    * Determines how a node calculates its transaction fees.
    */
    lateinit var fee: FeeStrategy

    /**
     * Determines how a node calculates transaction routes for multi-channel transactions.
     */
    lateinit var route: RoutingStrategy

    /**
     * Determines if a payment is relayed.
     */
    lateinit var paymentRelaying: PaymentRelayingStrategy
}
