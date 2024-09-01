/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import configuration.MiscConfigUtil.primaryExchangeEnvVar
import configuration.MiscConfigUtil.routingKeysEnvVars
import messaging.MessagingUtil.publishMessage
import startup.ConsumerProgram
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.RoutingKey

val DefaultProcessingConsumerQuantity = 5

object ExecutionCluster extends ConsumerProgram, IOApp:

  /** The entry point of the program.
    *
    * @param args
    *   The arguments passed to the program
    * @return
    *   An IO monad that represents the entry point of the program
    */
  def run(args: List[String]): IO[ExitCode] =
    val consumerAmount = args.headOption
      .flatMap(_.toIntOption)
      .getOrElse(DefaultProcessingConsumerQuantity)
    mainProgramHandler(consumerAmount).as(ExitCode.Success)
  end run

  override def createConsumer(
      channel: Channel
  ): IO[DefaultConsumer] =
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
end ExecutionCluster
