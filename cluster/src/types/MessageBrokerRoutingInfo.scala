package types

import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerRoutingKey

final case class MessageBrokerRoutingInfo(
    exchangeName: MessageBrokerExchange,
    routingKey: MessageBrokerRoutingKey
)
