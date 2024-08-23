/** @author
  *   Esteban Gonzalez Ruales
  */

package messaging

import cats.effect.IO
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP.Confirm
import com.rabbitmq.client.AMQP.Exchange
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import types.ExchangeType
import types.OpaqueTypes.*
import cats.effect.kernel.Resource

/** Provides utility functions to interact with the RabbitMQ broker.
  */
case object MessagingUtil:
  /** Creates a connection to the RabbitMQ broker.
    *
    * @param host
    *   The RabbitMQ host
    * @param port
    *   The RabbitMQ port
    * @param username
    *   The RabbitMQ username
    * @param password
    *   The RabbitMQ password
    *
    * @return
    *   A Resource monad with the RabbitMQ connection
    */
  def brokerConnection(
      host: String,
      port: Int,
      username: String,
      password: String
  ): Resource[IO, Connection] =
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(username)
    factory.setPassword(password)

    Resource.make(IO.delay(factory.newConnection()))(connection =>
      IO.delay(connection.close())
    )
  end brokerConnection

  /** Creates a channel to the defined RabbitMQ connection.
    *
    * @param connection
    *   The RabbitMQ connection
    *
    * @return
    *   A Resource monad with the RabbitMQ channel
    */
  def channelFromConnection(
      connection: Connection
  ): Resource[IO, Channel] =
    Resource.make(IO.delay(connection.createChannel()))(channel =>
      IO.delay(channel.close())
    )

  /** Creates an exchange on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param exchangeName
    *   The name of the exchange
    * @param exchangeType
    *   The type of the exchange
    * @param durable
    *   The durability of the exchange
    * @param autoDelete
    *   The auto-deletion of the exchange
    * @param internal
    *   The internal flag of the exchange
    *
    * @return
    *   An IO monad with the operation result of creating the exchange
    */
  def createExchange(
      channel: Channel,
      exchangeName: ExchangeName,
      exchangeType: ExchangeType,
      durable: Boolean = true,
      autoDelete: Boolean = false,
      internal: Boolean = false
  ): IO[Exchange.DeclareOk] =
    IO.delay(
      channel.exchangeDeclare(
        exchangeName.value,
        exchangeType.strValue,
        durable,
        autoDelete,
        internal,
        null
      )
    )

  /** Deletes an exchange on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param exchangeName
    *   The name of the exchange
    * @param ifUnused
    *   The ifUnused flag
    *
    * @return
    *   An IO monad with the operation result of deleting the exchange
    */
  def deleteExchange(
      channel: Channel,
      exchangeName: ExchangeName,
      ifUnused: Boolean = true
  ): IO[Exchange.DeleteOk] =
    IO.delay(
      channel.exchangeDelete(
        exchangeName.value,
        ifUnused
      )
    )

  /** Creates a queue on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param queueName
    *   The name of the queue
    * @param durable
    *   The durability of the queue
    * @param exclusive
    *   The exclusivity of the queue
    * @param autoDelete
    *   The auto-deletion of the queue
    *
    * @return
    *   An IO monad with the operation result of creating the queue
    */
  def createQueue(
      channel: Channel,
      queueName: QueueName,
      durable: Boolean = true,
      exclusive: Boolean = false,
      autoDelete: Boolean = false
  ): IO[Queue.DeclareOk] =
    IO.delay(
      channel.queueDeclare(
        queueName.value,
        durable,
        exclusive,
        autoDelete,
        null
      )
    )

  /** Deletes a queue on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param queueName
    *   The name of the queue
    * @param ifUsed
    *   The ifUsed flag
    * @param ifEmpty
    *   The ifEmpty flag
    *
    * @return
    *   An IO monad with the operation result of deleting the queue
    */
  def deleteQueue(
      channel: Channel,
      queueName: QueueName,
      ifUsed: Boolean = true,
      ifEmpty: Boolean = true
  ): IO[Queue.DeleteOk] =
    IO.delay(
      channel.queueDelete(
        queueName.value,
        ifUsed,
        ifEmpty
      )
    )

  /** Binds a queue to an exchange on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param queueName
    *   The name of the queue
    * @param exchangeName
    *   The name of the exchange
    * @param routingKey
    *   The routing key
    *
    * @return
    *   An IO monad with the operation result of binding the queue to the
    *   exchange
    */
  def bindQueueWithExchange(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  ): IO[Queue.BindOk] =
    IO.delay(
      channel.queueBind(
        queueName.value,
        exchangeName.value,
        routingKey.value
      )
    )

  /** Binds an exchange to another exchange on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param sourceExchangeName
    *   The name of the source exchange
    * @param destinationExchangeName
    *   The name of the destination exchange
    * @param routingKey
    *   The routing key
    *
    * @return
    *   An IO monad with the operation result of binding the exchange to the
    *   other exchange
    */
  def bindExchangeWithExchange(
      channel: Channel,
      destinationExchangeName: ExchangeName,
      sourceExchangeName: ExchangeName,
      routingKey: RoutingKey
  ): IO[Exchange.BindOk] =
    IO.delay(
      channel.exchangeBind(
        destinationExchangeName.value,
        sourceExchangeName.value,
        routingKey.value
      )
    )

  /** Sets the quality of service on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param prefetchSize
    *   The prefetch size
    * @param prefetchCount
    *   The prefetch count
    * @param global
    *   The global flag
    *
    * @return
    *   An IO monad with the operation result of setting the quality of service
    */
  def defineQos(
      channel: Channel,
      prefetchSize: Int = 0,
      prefetchCount: Int = 0,
      global: Boolean = false
  ): IO[Unit] =
    IO.delay(channel.basicQos(prefetchSize, prefetchCount, global))

  /** Enables publisher confirms on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    *
    * @return
    *   An IO monad with the operation result of enabling publisher confirms
    */

  def definePublisherConfirms(channel: Channel): IO[Confirm.SelectOk] =
    IO.delay(channel.confirmSelect())

  /** The consumeMessages function consumes messages from a queue on the defined
    * RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param queueName
    *   The name of the queue
    * @param autoAck
    *   The auto-acknowledgement of the message
    * @param callback
    *   The message callback
    *
    * @return
    *   An IO monad with the operation result of consuming the messages
    */
  def consumeMessages(
      channel: Channel,
      queueName: QueueName,
      autoAck: Boolean = true,
      consumer: Consumer
  ): IO[String] =
    IO.delay(
      channel.basicConsume(
        queueName.value,
        autoAck,
        consumer
      )
    )

  /** Sends a message to an exchange on the defined RabbitMQ channel.
    *
    * @param channel
    *   The RabbitMQ channel
    * @param exchangeName
    *   The name of the exchange
    * @param routingKey
    *   The routing key
    * @param messageBytes
    *   The message bytes
    * @param properties
    *   The message properties
    *
    * @return
    *   An IO monad with the operation result of sending the message
    */
  def publishMessage(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      messageBytes: Seq[Byte],
      properties: BasicProperties = null
  ): Unit =
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      properties,
      messageBytes.toArray
    )
end MessagingUtil
