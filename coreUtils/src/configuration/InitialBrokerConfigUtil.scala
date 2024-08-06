/** @author
  *   Esteban Gonzalez Ruales
  */

package configuration

import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import com.rabbitmq.client.Channel
import configuration.ExternalResources.stringFromFilepath
import messaging.MessagingUtil.bindedQueueWithExchange
import messaging.MessagingUtil.channelWithExchange
import messaging.MessagingUtil.channelWithQueue
import org.virtuslab.yaml.*
import types.ExchangeType
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey
import types.YamlExchange
import types.YamlQueue

/** The InitialBrokerConfigUtil object provides utility functions to configure
  * the message broker from environment variables and predefined elements of the
  * system. This object is intended as a possible startup configuration for the
  * broker but not continuous configuration.
  */
object InitialBrokerConfigUtil:

  /** The executeInitialBrokerConfiguration function configures the message
    * broker with the predefined elements of the system.
    *
    * @param channel
    *   the channel to communicate with the message broker
    *
    * @return
    *   an IO monad with the result of the operation
    */
  def executeInitialBrokerConfiguration(channel: Channel): IO[Unit] =
    for
      exchanges <- getBrokerExchangeConfiguration
      queues <- getBrokerQueueConfiguration
      _ <- configureBrokerExchanges(channel, exchanges)
      _ <- configureBrokerQueues(channel, queues)
    yield ()

  /** The getBrokerExchangeConfiguration function reads the exchange
    * configurations from a yaml file.
    *
    * @return
    *   an IO monad with the exchange configurations
    */
  def getBrokerExchangeConfiguration: IO[Map[String, YamlExchange]] =
    stringFromFilepath(
      os.pwd / "globalProcessing" / "resources" / "exchanges.yaml"
    ).use(text =>
      IO.fromOption(text.as[Map[String, YamlExchange]].toOption)(
        Exception("Exchange file not found")
      )
    )

  /** The getBrokerQueueConfiguration function reads the queue configurations
    * from a yaml file.
    *
    * @return
    *   an IO monad with the queue configurations
    */
  def getBrokerQueueConfiguration: IO[Map[String, YamlQueue]] =
    stringFromFilepath(
      os.pwd / "globalProcessing" / "resources" / "queues.yaml"
    ).use(text =>
      IO.fromOption(text.as[Map[String, YamlQueue]].toOption)(
        Exception("Queues file not found")
      )
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
    for
      exchangeName <- IO.pure(ExchangeName(yamlExchange.exchangeName))
      exchangeType <- IO.fromOption(
        ExchangeType.values.find(_.strValue == yamlExchange.exchangeType)
      )(
        Exception("Exchange type not found")
      )
      _ <- channelWithExchange(
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
