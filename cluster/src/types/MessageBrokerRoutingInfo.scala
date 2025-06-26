package types

import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerRoutingKey

final case class MessageBrokerRoutingInfo(
    exchange: MessageBrokerExchange,
    routingKey: MessageBrokerRoutingKey
)
