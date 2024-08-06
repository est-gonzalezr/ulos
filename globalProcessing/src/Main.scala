import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.effect.std.Console
import messaging.MessagingUtil.brokerConnection
import messaging.MessagingUtil.channelFromConnection
import org.virtuslab.yaml.*
import types.YamlExchange
import types.YamlQueue
import com.rabbitmq.client.Connection
import configuration.ExternalResources.stringFromFilepath
import configuration.InitialBrokerConfigUtil.configureBrokerExchanges
import configuration.InitialBrokerConfigUtil.configureBrokerQueues
import cats.syntax.traverse.toTraverseOps
import types.BrokerExchange
import types.BrokerQueue
import cats.effect.kernel.Ref
import configuration.MiscConfigUtil.getBrokerEnvironmentVariables
import com.rabbitmq.client.Channel
import scala.util.Try
import configuration.InitialBrokerConfigUtil.executeInitialBrokerConfiguration
import types.ExchangeType
import com.rabbitmq.client.AMQP.Exchange
import types.OpaqueTypes.ExchangeName
import types.ExchangeType
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey
import messaging.MessagingUtil.channelWithExchange
import messaging.MessagingUtil.channelWithQueue
import messaging.MessagingUtil.bindedQueueWithExchange
import messaging.MessagingUtil.channelWithoutExchange
import messaging.MessagingUtil.channelWithoutQueue

@main
def main(args: String*): Unit =
  println("Hello world!")
  val program =
    for
      envVars <- getBrokerEnvironmentVariables
      host <- IO.fromOption(envVars.get("host"))(Exception("host not found"))
      port <- IO.fromOption(envVars.get("port"))(Exception("port not found"))
      user <- IO.fromOption(envVars.get("user"))(Exception("user not found"))
      pass <- IO.fromOption(envVars.get("pass"))(Exception("pass not found"))
      portInt <- IO.fromOption(port.toIntOption)(Exception("port not an int"))
      _ <- brokerConnection(host, portInt, user, pass)
        .use(connection =>
          channelFromConnection(connection).use(channel =>
            mainProgramLoop(channel)
          )
        )
    yield ()

  program
    .handleErrorWith(e => Console[IO].println(e.printStackTrace()))
    .foreverM
    .unsafeRunSync()

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
        _ <- channelWithExchange(
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
        _ <- channelWithQueue(
          channel,
          queue.queueName,
          queue.durable,
          queue.exclusive,
          queue.autoDelete
        )
        _ <- bindedQueueWithExchange(
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
        _ <- channelWithoutExchange(channel, exchangeName)
      yield ()
    case 5 =>
      for
        queue <- queueDeleteInput
        queueName <- IO.fromTry(Try(QueueName(queue)))
        _ <- channelWithoutQueue(channel, queueName)
      yield ()

    case _ => IO.unit

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
    internal,
    Set.empty
  )

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
