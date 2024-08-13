import ConsoleUtil.exchangeDeleteInput
import ConsoleUtil.newExchangeInput
import ConsoleUtil.newQueueInput
import ConsoleUtil.options
import ConsoleUtil.queueDeleteInput
import InitialBrokerConfigUtil.executeInitialBrokerConfiguration
import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse.toTraverseOps
import com.rabbitmq.client.AMQP.Exchange
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import configuration.MiscConfigUtil.brokerEnvironmentVariables
import messaging.MessagingUtil.bindQueueWithExchange
import messaging.MessagingUtil.brokerConnection
import messaging.MessagingUtil.channelFromConnection
import messaging.MessagingUtil.createExchange
import messaging.MessagingUtil.createQueue
import messaging.MessagingUtil.deleteExchange
import messaging.MessagingUtil.deleteQueue
import types.BrokerExchange
import types.BrokerQueue
import types.ExchangeType
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey

import scala.util.Try

@main
def main(args: String*): Unit =
  connectionHandler.unsafeRunSync()

/** The connectionHandler function is the entry point of the program. It is
  * responsible for establishing a connection with the broker and handling any
  * exceptions that may occur.
  *
  * @return
  *   an IO monad that represents the connection handler.
  */
def connectionHandler: IO[Nothing] =
  (
    for
      envVars <- brokerEnvironmentVariables
      host <- IO.fromOption(envVars.get("host"))(Exception("host not found"))
      port <- IO.fromOption(envVars.get("port"))(Exception("port not found"))
      user <- IO.fromOption(envVars.get("user"))(Exception("user not found"))
      pass <- IO.fromOption(envVars.get("pass"))(Exception("pass not found"))
      portInt <- IO.fromOption(port.toIntOption)(Exception("port not an int"))
      _ <- brokerConnection(host, portInt, user, pass).use(connection =>
        channelHandler(connection)
      )
    yield ()
  ).handleErrorWith(Console[IO].printStackTrace).foreverM

/** The channelHandler function is responsible for handling the channel that is
  * created from the connection. It is responsible for handling any exceptions
  * that may occur.
  *
  * @param connection
  *   the connection that the channel is created from.
  * @return
  *   an IO monad that represents the channel handler.
  */
def channelHandler(connection: Connection): IO[Nothing] =
  channelFromConnection(connection)
    .use(channel => mainProgramLoop(channel))
    .handleErrorWith(Console[IO].printStackTrace)
    .foreverM

/** The mainProgramLoop function is the main loop of the program. It is
  * responsible for displaying the options to the user and executing the
  * selected option.
  *
  * @param channel
  *   the channel that the program is using.
  * @return
  *   an IO monad that represents the main loop of the program.
  */
def mainProgramLoop(
    channel: Channel
): IO[Nothing] =
  (
    for
      _ <- Console[IO].println(
        "Enter the number of the option you want to execute:"
      )
      _ <- options.traverse((number, description) =>
        Console[IO].println(s"$number: $description")
      )
      input <- Console[IO].readLine
      _ <- executeInput(channel, input.toInt)
    yield ()
  ).foreverM

/** The executeInput function is responsible for executing the selected option.
  *
  * @param channel
  *   the channel that the program is using.
  * @param input
  *   the selected option.
  * @return
  *   an IO monad that represents the execution of the selected option.
  */
def executeInput(
    channel: Channel,
    input: Int
): IO[Unit] =
  input match
    case 1 =>
      executeInitialBrokerConfiguration(channel)
    case 2 =>
      for
        exchangeArgs <- newExchangeInput
        exchange <- IO.fromTry(newExchange(exchangeArgs))
        _ <- createExchange(
          channel,
          exchange.exchangeName,
          exchange.exchangeType,
          exchange.durable,
          exchange.autoDelete,
          exchange.internal
        )
      yield ()
    case 3 =>
      for
        queueArgs <- newQueueInput
        queue <- IO.fromTry(newQueue(queueArgs))
        _ <- createQueue(
          channel,
          queue.queueName,
          queue.durable,
          queue.exclusive,
          queue.autoDelete
        )
        _ <- bindQueueWithExchange(
          channel,
          queue.queueName,
          queue.exchangeName,
          queue.routingKey
        )
      yield ()
    case 4 =>
      for
        exchange <- exchangeDeleteInput
        exchangeName <- IO.fromTry(Try(ExchangeName(exchange)))
        _ <- deleteExchange(channel, exchangeName)
      yield ()
    case 5 =>
      for
        queue <- queueDeleteInput
        queueName <- IO.fromTry(Try(QueueName(queue)))
        _ <- deleteQueue(channel, queueName)
      yield ()

    case _ => IO.unit

/** The newExchange function is responsible for creating a new exchange from the
  * user input.
  *
  * @param args
  *   the arguments that the user has entered.
  * @return
  *   a Try monad that represents the creation of a new exchange.
  */
def newExchange(args: Map[String, String]): Try[BrokerExchange] =
  for
    exchangeName <- Try(ExchangeName(args.get("exchangeName").get))
    exchangeType <- Try(
      ExchangeType.values.find(_.strValue == args.get("exchangeType").get).get
    )
    durable <- Try(args.get("durable").get.toBoolean)
    autoDelete <- Try(args.get("autoDelete").get.toBoolean)
    internal <- Try(args.get("internal").get.toBoolean)
  yield BrokerExchange(
    exchangeName,
    exchangeType,
    durable,
    autoDelete,
    internal
  )

/** The newQueue function is responsible for creating a new queue from the user
  * input.
  *
  * @param args
  *   the arguments that the user has entered.
  * @return
  *   a Try monad that represents the creation of a new queue.
  */
def newQueue(args: Map[String, String]): Try[BrokerQueue] =
  for
    queueName <- Try(QueueName(args.get("queueName").get))
    exchangeName <- Try(ExchangeName(args.get("exchangeName").get))
    durable <- Try(args.get("durable").get.toBoolean)
    exclusive <- Try(args.get("exclusive").get.toBoolean)
    autoDelete <- Try(args.get("autoDelete").get.toBoolean)
    routingKey <- Try(RoutingKey(args.get("routingKey").get))
  yield BrokerQueue(
    queueName,
    exchangeName,
    durable,
    exclusive,
    autoDelete,
    routingKey
  )
