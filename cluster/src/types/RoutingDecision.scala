package types

import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerRoutingKey
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

case class RoutingDecision(
    exchange: MessageBrokerExchange,
    routingKey: MessageBrokerRoutingKey,
    successRoutingDecision: Option[RoutingDecision],
    failureRoutingDecision: Option[RoutingDecision]
)

object RoutingDecision:
  given JsonDecoder[RoutingDecision] = DeriveJsonDecoder.gen[RoutingDecision]
  given JsonEncoder[RoutingDecision] = DeriveJsonEncoder.gen[RoutingDecision]
end RoutingDecision
