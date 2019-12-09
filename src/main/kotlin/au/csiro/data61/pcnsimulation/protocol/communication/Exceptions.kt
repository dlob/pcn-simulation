package au.csiro.data61.pcnsimulation.protocol.communication

import au.csiro.data61.pcnsimulation.IPAddress

/**
 * An [IPNetwork] related exception.
 */
abstract class IPNetworkException(errorMessage: String) : Exception(errorMessage)

/**
 * Exception that is raised if an [IPSocket] is not ready to send data.
 */
class SocketClosedException(
        ipAddress: IPAddress
) : IPNetworkException("IPNetwork socket ($ipAddress) is closed.")

/**
 * Exception that is raised if an [IPSocket] receives an unexpected response packet.
 */
class UnexpectedResponseReceivedException(
        packet: IPPacket
) : IPNetworkException("Socket ${packet.receiver} received an unexpected packet from ${packet.sender} (${packet.payload.javaClass.simpleName}).")

/**
 * Exception that is raised if no [IPSocket] with the address of the receiver can be found by an [IPNetwork].
 */
class PacketUndeliverableException(
        packet: IPPacket
) : IPNetworkException("Packet couldn't be delivered: ${packet.sender} -X-> ${packet.receiver} (${packet.payload.javaClass.simpleName}).")

/**
 * Exception that is raised if a request has not been answered (in time).
 */
class RequestUnansweredException(
        sender: IPAddress,
        receiver: IPAddress,
        request: Any
) : IPNetworkException("Request has not been answered: $sender <-X- $receiver (${request.javaClass.simpleName}).")
