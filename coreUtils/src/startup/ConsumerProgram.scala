/** @author
  *   Esteban Gonzalez Ruales
  */

package startup

import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.catsSyntaxParallelSequence1
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DefaultConsumer
import configuration.MiscConfigUtil.brokerEnvVars
import configuration.MiscConfigUtil.consumptionQueueEnvVar
import messaging.MessagingUtil.brokerConnection
import messaging.MessagingUtil.channelFromConnection
import messaging.MessagingUtil.consumeMessages
import types.OpaqueTypes.QueueName

val DefaultProcessingConsumerQuantity = 5

/** Represents a program that consumes messages from a message broker.
  */
trait ConsumerProgram:

  /** The main program that consumes messages from a message broker.
    *
    * @param consumerAmount
    *   The amount of consumers that will be created to consume messages
    */
  def mainProgram(consumerAmount: Int): Unit =
    initializeProgram(
      Option(consumerAmount)
        .filter(_ > 0)
        .getOrElse(DefaultProcessingConsumerQuantity)
    )
      .handleErrorWith(Console[IO].printStackTrace)
      .foreverM
      .unsafeRunSync()

  /** Initializes the program by reading the environment variables required to
    * configure the message broker and creating the consumers.
    *
    * @param consumerAmount
    *   The amount of consumers that will be created to consume messages
    *
    * @return
    *   An IO monad with that represents the initialization of the program
    */
  def initializeProgram(consumerAmount: Int): IO[Unit] =
    for
      envVars <- brokerEnvVars
      host <- IO.fromOption(envVars.get("host"))(Exception("host not found"))
      port <- IO.fromOption(envVars.get("port"))(Exception("port not found"))
      user <- IO.fromOption(envVars.get("user"))(Exception("user not found"))
      pass <- IO.fromOption(envVars.get("pass"))(Exception("pass not found"))
      portInt <- IO.fromOption(port.toIntOption)(Exception("port not an int"))
      _ <- brokerConnection(host, portInt, user, pass).use(connection =>
        createQueueConsumers(connection, consumerAmount).void
      )
    yield ()

  /** Creates the consumers that will consume messages from the message broker.
    *
    * @param connection
    *   The connection to the message broker
    * @param quantity
    *   The amount of consumers that will be created
    *
    * @return
    *   An IO monad with the consumers created
    */
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

  /** The program that consumes messages from a message broker.
    *
    * @param connection
    *   The connection to the message broker
    *
    * @return
    *   An IO monad with the program that consumes messages
    */
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

  /** Creates a consumer that consumes messages from a message broker.
    *
    * @param channel
    *   The channel to the message broker
    *
    * @return
    *   An IO monad with the consumer created
    */
  def createConsumer(channel: Channel): IO[DefaultConsumer]
end ConsumerProgram
