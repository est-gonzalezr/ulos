import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.catsSyntaxParallelSequence1
import com.rabbitmq.client.Connection
import configuration.MiscConfigUtil.brokerEnvironmentVariables
import messaging.MessagingUtil
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
    envVars <- brokerEnvironmentVariables
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
  List.fill(quantity)(queueConsumerProgram(connection)).parSequence()

def queueConsumerProgram(connection: Connection): IO[Unit] =
  channelFromConnection(connection)
    .use(channel =>
      val successRoutingKey = RoutingKey("success")
      val errorRoutingKey = RoutingKey("error")
      val exchangeName = ExchangeName("global_processing_exchange")
      val publishFunction =
        publishMessage(channel, exchangeName, _, _)
      val consumer = ExecutionConsumer(
        channel,
        successRoutingKey,
        errorRoutingKey,
        publishFunction
      )

      for
        _ <- consumeMessages(
          channel,
          QueueName("hello"),
          false,
          consumer
        )
        _ <- IO.delay(Thread.sleep(60000)).foreverM
      yield ()
    )
    .handleErrorWith(Console[IO].printStackTrace)
    .foreverM
