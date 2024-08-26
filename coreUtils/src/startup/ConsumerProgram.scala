/** @author
  *   Esteban Gonzalez Ruales
  */

package startup

import cats.effect.IO
import cats.effect.std.Console
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

import scala.concurrent.duration.*

val DefaultProcessingConsumerQuantity = 5

/** Represents a program that consumes messages from a message broker.
  */
trait ConsumerProgram:

  /** The entry point representation of the program. It is responsible for
    * starting the main program and handling any exceptions that may occur.
    *
    * @param args
    *   The arguments passed to the program
    *
    * @return
    *   An IO monad that represents the entry point of the program
    */
  def mainProgramHandler(consumerAmount: Int): IO[Nothing] =
    mainProgram(consumerAmount)
      .handleErrorWith(Console[IO].printStackTrace)
      .foreverM

  /** The main program that defines starts and consumes messages from a message
    * broker.
    *
    * @param consumerAmount
    *   The amount of consumers that will be created to consume messages
    *
    * @return
    *   An IO monad that represents the main program
    */
  def mainProgram(consumerAmount: Int): IO[Unit] =
    initializeProgram(
      Option(consumerAmount)
        .filter(_ > 0)
        .getOrElse(DefaultProcessingConsumerQuantity)
    )

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
        queueConsumerProgramHandler(connection)
      )
      .parSequence()

  /** The handler for the program that consumes messages from a message broker.
    * It is responsible for handling any exceptions that may occur.
    *
    * @param connection
    *   The connection to the message broker
    *
    * @return
    *   An IO monad that represents the handler for the program
    */
  def queueConsumerProgramHandler(connection: Connection): IO[Nothing] =
    queueConsumerProgram(connection)
      .handleErrorWith(Console[IO].printStackTrace)
      .foreverM

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
            consumer,
            false
          )
          _ <- IO.sleep(60.second).foreverM
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
