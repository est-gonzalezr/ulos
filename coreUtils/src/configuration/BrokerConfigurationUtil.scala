/** @author
  *   Esteban Gonzalez Ruales
  */

package configuration

import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import com.rabbitmq.client.Channel
import configuration.ExternalConfigurationUtil.environmentVariableMap
import messaging.MessagingUtil.bindedQueueWithExchange
import messaging.MessagingUtil.channelWithExchange
import messaging.MessagingUtil.channelWithQueue
import types.ExchangeType
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey
import types.YamlExchange
import types.YamlQueue

/** The BrokerConfigurationUtil object provides utility functions to configure
  * the message broker from environment variables and predefined elements of the
  * system. This object is intended as a startup configuration of the broker but
  * not continuous configuration. The functions are implemented using the IO
  * monad from the Cats Effect library to abstract away side effects.
  */
object BrokerConfigurationUtil:

  /** The getBrokerEnvironmentVariables function reads the environment variables
    * required to configure the message broker and returns them as a map.
    *
    * @return
    *   an IO monad with the environment variables as a map
    */
  def getBrokerEnvironmentVariables: IO[Map[String, String]] =
    for
      envMap <- environmentVariableMap
      rabbitmqHost <- IO.fromOption(envMap.get("RABBITMQ_HOST"))(
        Exception("RABBITMQ_HOST not found in environment variables")
      )
      rabbitmqPort <- IO.fromOption(envMap.get("RABBITMQ_PORT"))(
        Exception("RABBITMQ_PORT not found in environment variables")
      )
      rabbitmqUser <- IO.fromOption(envMap.get("RABBITMQ_USER"))(
        Exception("RABBITMQ_USER not found in environment variables")
      )
      rabbitmqPass <- IO.fromOption(envMap.get("RABBITMQ_PASS"))(
        Exception("RABBITMQ_PASS not found in environment variables")
      )
    yield Map(
      "host" -> rabbitmqHost,
      "port" -> rabbitmqPort,
      "user" -> rabbitmqUser,
      "pass" -> rabbitmqPass
    )

  /** The configureBrokerExchanges function configures the exchanges in the
    * message broker.
    *
    * @param channel
    *   the channel to communicate with the message broker
    * @param exchanges
    *   the map of exchange configurations
    *
    * @return
    *   an IO monad with the result of the operation
    */
  def configureBrokerExchanges(
      channel: Channel,
      exchanges: Map[String, YamlExchange]
  ): IO[List[Unit]] =
    val ops =
      for (_, exchange) <- exchanges.toList
      yield configureBrokerExchange(channel, exchange)

    ops.sequence

  /** The configureBrokerExchange function configures an exchange in the message
    * broker.
    *
    * @param channel
    *   the channel to communicate with the message broker
    * @param yamlExchange
    *   the exchange configuration
    *
    * @return
    *   an IO monad with the result of the operation
    */
  private def configureBrokerExchange(
      channel: Channel,
      yamlExchange: YamlExchange
  ): IO[Unit] =
    val exchangeName = ExchangeName(yamlExchange.exchangeName)
    val exchangeType = yamlExchange.exchangeType match
      case "direct"  => ExchangeType.Direct
      case "fanout"  => ExchangeType.Fanout
      case "topic"   => ExchangeType.Topic
      case "headers" => ExchangeType.Headers
      case _         => ExchangeType.Direct

    for _ <- channelWithExchange(
        channel,
        exchangeName,
        exchangeType,
        yamlExchange.durable,
        yamlExchange.autoDelete,
        yamlExchange.internal
      )
    yield ()

  /** The configureBrokerQueues function configures the queues in the message
    * broker.
    *
    * @param channel
    *   the channel to communicate with the message broker
    * @param queues
    *   the map of queue configurations
    *
    * @return
    *   an IO monad with the result of the operation
    */
  def configureBrokerQueues(
      channel: Channel,
      queues: Map[String, YamlQueue]
  ): IO[List[Unit]] =
    val ops =
      for (_, queue) <- queues.toList
      yield configureBrokerQueue(channel, queue)

    ops.sequence

  /** The configureBrokerQueue function configures a queue in the message
    * broker.
    *
    * @param channel
    *   the channel to communicate with the message broker
    * @param queue
    *   the queue configuration
    *
    * @return
    *   an IO monad with the result of the operation
    */
  private def configureBrokerQueue(
      channel: Channel,
      queue: YamlQueue
  ): IO[Unit] =
    val queueName = QueueName(queue.queueName)
    val exchangeName = ExchangeName(queue.exchangeName)

    for
      _ <- channelWithQueue(
        channel,
        queueName,
        queue.durable,
        queue.exclusive,
        queue.autoDelete
      )
      _ <- bindedQueueWithExchange(
        channel,
        queueName,
        exchangeName,
        RoutingKey(queue.routingKey)
      )
    yield ()
