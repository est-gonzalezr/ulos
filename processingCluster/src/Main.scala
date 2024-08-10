import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.catsSyntaxParallelSequence1
import com.rabbitmq.client.Connection
import configuration.MiscConfigUtil.getBrokerEnvironmentVariables
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
  (for
    envVars <- getBrokerEnvironmentVariables
    host <- IO.fromOption(envVars.get("host"))(Exception("host not found"))
    port <- IO.fromOption(envVars.get("port"))(Exception("port not found"))
    user <- IO.fromOption(envVars.get("user"))(Exception("user not found"))
    pass <- IO.fromOption(envVars.get("pass"))(Exception("pass not found"))
    portInt <- IO.fromOption(port.toIntOption)(Exception("port not an int"))
    _ <- initializeProgram(host, portInt, user, pass)
  yield ())
    .handleErrorWith(Console[IO].printStackTrace)
    .foreverM
    .unsafeRunSync()

def initializeProgram(
    host: String,
    port: Int,
    user: String,
    pass: String
): IO[Unit] =
  brokerConnection(host, port, user, pass).use(connection =>
    for _ <- createQueueConsumers(connection, DefaultProcessingConsumerQuantity)
    yield ()
  )

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
      val consumer = QueueConsumer(
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
