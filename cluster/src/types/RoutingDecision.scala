package types

import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerQueue

case class RoutingDecision(
    currentExchange: MessageBrokerExchange,
    currentRoutingKey: MessageBrokerQueue,
    successRoutingDecision: RoutingDecision,
    failureRoutingDecision: RoutingDecision
)
