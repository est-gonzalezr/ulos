import cats.effect.IO
import com.rabbitmq.client.Channel
import configuration.MiscConfigUtil.primaryExchangeEnvVar
import configuration.MiscConfigUtil.routingKeysEnvVars
import messaging.MessagingUtil.publishMessage
import startup.ConsumerProgram
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.RoutingKey

object ExecutionCluster extends ConsumerProgram:
  @main def main = mainProgram(5)

  override def createConsumer(channel: Channel): IO[ExecutionConsumer] =
    for
      routingKeysEnvVars <- routingKeysEnvVars
      publishingRoutingKey <- IO.fromOption(
        routingKeysEnvVars.get("publishing_routing_key")
      )(Exception("publishing_routing_key not found"))
      databaseRoutingKey <- IO.fromOption(
        routingKeysEnvVars.get("database_routing_key")
      )(Exception("database_routing_key not found"))
      primaryExchange <- primaryExchangeEnvVar
    yield ExecutionConsumer(
      channel,
      RoutingKey(publishingRoutingKey),
      RoutingKey(databaseRoutingKey),
      publishMessage(
        channel,
        ExchangeName(primaryExchange),
        _,
        _
      )
    )
