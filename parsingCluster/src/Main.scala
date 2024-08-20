import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.catsSyntaxParallelSequence1
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import configuration.MiscConfigUtil.brokerEnvVars
import configuration.MiscConfigUtil.consumptionQueueEnvVar
import configuration.MiscConfigUtil.primaryExchangeEnvVar
import configuration.MiscConfigUtil.routingKeysEnvVars
import messaging.MessagingUtil.brokerConnection
import messaging.MessagingUtil.channelFromConnection
import messaging.MessagingUtil.consumeMessages
import messaging.MessagingUtil.publishMessage
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey

val DefaultProcessingConsumerQuantity = 5

@main
def main: Unit =
  initializeProgram
    .handleErrorWith(Console[IO].printStackTrace)
    .foreverM
    .unsafeRunSync()

def initializeProgram: IO[Unit] =
  for
    envVars <- brokerEnvVars
    host <- IO.fromOption(envVars.get("host"))(Exception("host not found"))
    port <- IO.fromOption(envVars.get("port"))(Exception("port not found"))
    user <- IO.fromOption(envVars.get("user"))(Exception("user not found"))
    pass <- IO.fromOption(envVars.get("pass"))(Exception("pass not found"))
    portInt <- IO.fromOption(port.toIntOption)(Exception("port not an int"))
    _ <- brokerConnection(host, portInt, user, pass).use(connection =>
      createQueueConsumers(connection, DefaultProcessingConsumerQuantity).void
    )
  yield ()

def createQueueConsumers(
    connection: Connection,
    quantity: Int
): IO[List[Unit]] =
  List
    .fill(quantity)(
      queueConsumerProgram(connection)
        .handleErrorWith(Console[IO].printStackTrace)
        .foreverM
    )
    .parSequence()

def queueConsumerProgram(connection: Connection): IO[Unit] =
  channelFromConnection(connection)
    .use(channel =>
      for
        consumer <- createConsumer(channel)
        queue <- consumptionQueueEnvVar
        _ <- consumeMessages(
          channel,
          QueueName(queue),
          false,
          consumer
        )
        _ <- IO.delay(Thread.sleep(60000)).foreverM
      yield ()
    )

def createConsumer(channel: Channel): IO[ParsingConsumer] =
  for
    routingKeysEnvVars <- routingKeysEnvVars
    publishingRoutingKey <- IO.fromOption(
      routingKeysEnvVars.get("publishing_routing_key")
    )(Exception("publishing_routing_key not found"))
    databaseRoutingKey <- IO.fromOption(
      routingKeysEnvVars.get("database_routing_key")
    )(Exception("database_routing_key not found"))
    primaryExchange <- primaryExchangeEnvVar
  yield ParsingConsumer(
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
