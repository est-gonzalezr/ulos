/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import com.rabbitmq.client.Channel
import configuration.ExternalResources.stringFromFilepath
import messaging.MessagingUtil.bindQueueWithExchange
import messaging.MessagingUtil.createExchange
import messaging.MessagingUtil.createQueue
import org.virtuslab.yaml.StringOps
import types.ExchangeType
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey
import types.YamlExchange
import types.YamlQueue

/** Provides utility functions to configure the message broker from environment
  * variables and predefined elements of the system. This object is intended as
  * a possible startup configuration for the broker but not continuous
  * configuration.
  */
case object InitialBrokerConfigUtil:

  /** Configures the message broker with the predefined elements of the system.
    *
    * @param channel
    *   The channel to communicate with the message broker
    *
    * @return
    *   An IO monad with the result of the operation
    */
  def executeInitialBrokerConfiguration(channel: Channel): IO[Unit] =
    for
      exchanges <- getBrokerExchangeConfiguration
      queues <- getBrokerQueueConfiguration
      _ <- configureBrokerExchanges(channel, exchanges)
      _ <- configureBrokerQueues(channel, queues)
    yield ()

  /** Reads the exchange configurations from a yaml file.
    *
    * @return
    *   An IO monad with the exchange configurations
    */
  def getBrokerExchangeConfiguration: IO[Map[String, YamlExchange]] =
    stringFromFilepath(
      os.pwd / "globalProcessing" / "resources" / "exchanges.yaml"
    ).use(text =>
      IO.fromOption(text.as[Map[String, YamlExchange]].toOption)(
        Exception("Exchange file not found")
      )
    )

  /** Reads the queue configurations from a yaml file.
    *
    * @return
    *   An IO monad with the queue configurations
    */
  def getBrokerQueueConfiguration: IO[Map[String, YamlQueue]] =
    stringFromFilepath(
      os.pwd / "globalProcessing" / "resources" / "queues.yaml"
    ).use(text =>
      IO.fromOption(text.as[Map[String, YamlQueue]].toOption)(
        Exception("Queues file not found")
      )
    )

  /** Configures the exchanges in the message broker.
    *
    * @param channel
    *   The channel to communicate with the message broker
    * @param exchanges
    *   The map of exchange configurations
    *
    * @return
    *   An IO monad with the result of the operation
    */
  def configureBrokerExchanges(
      channel: Channel,
      exchanges: Map[String, YamlExchange]
  ): IO[List[Unit]] =
    val ops =
      for (_, exchange) <- exchanges.toList
      yield configureBrokerExchange(channel, exchange)

    ops.sequence

  /** Configures an exchange in the message broker.
    *
    * @param channel
    *   The channel to communicate with the message broker
    * @param yamlExchange
    *   The exchange configuration
    *
    * @return
    *   An IO monad with the result of the operation
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
      _ <- createExchange(
        channel,
        exchangeName,
        exchangeType,
        yamlExchange.durable,
        yamlExchange.autoDelete,
        yamlExchange.internal
      )
    yield ()

  /** Configures the queues in the message broker.
    *
    * @param channel
    *   The channel to communicate with the message broker
    * @param queues
    *   The map of queue configurations
    *
    * @return
    *   An IO monad with the result of the operation
    */
  def configureBrokerQueues(
      channel: Channel,
      queues: Map[String, YamlQueue]
  ): IO[List[Unit]] =
    val ops =
      for (_, queue) <- queues.toList
      yield configureBrokerQueue(channel, queue)

    ops.sequence

  /** Configures a queue in the message broker.
    *
    * @param channel
    *   The channel to communicate with the message broker
    * @param queue
    *   The queue configuration
    *
    * @return
    *   An IO monad with the result of the operation
    */
  private def configureBrokerQueue(
      channel: Channel,
      queue: YamlQueue
  ): IO[Unit] =
    val queueName = QueueName(queue.queueName)
    val exchangeName = ExchangeName(queue.exchangeName)

    for
      _ <- createQueue(
        channel,
        queueName,
        queue.durable,
        queue.exclusive,
        queue.autoDelete
      )
      _ <- bindQueueWithExchange(
        channel,
        queueName,
        exchangeName,
        RoutingKey(queue.routingKey)
      )
    yield ()
