import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.catsSyntaxParallelSequence1
import com.rabbitmq.client.Connection
import configuration.MiscConfigUtil.brokerEnvVars
import messaging.MessagingUtil
import messaging.MessagingUtil.brokerConnection
import messaging.MessagingUtil.channelFromConnection
import messaging.MessagingUtil.consumeMessages
import types.OpaqueTypes.QueueName

val DefaultProcessingConsumerQuantity = 1

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
  List.fill(quantity)(queueConsumerProgram(connection)).parSequence()

def queueConsumerProgram(connection: Connection): IO[Unit] =
  channelFromConnection(connection)
    .use(channel =>
      val consumer = DatabaseConsumer(channel)

      for
        _ <- consumeMessages(
          channel,
          QueueName("database_queue"),
          false,
          consumer
        )
        _ <- IO.delay(Thread.sleep(60000)).foreverM
      yield ()
    )
    .handleErrorWith(Console[IO].printStackTrace)
    .foreverM
