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

/** The MessagingUtil object provides utility functions to interact with the
  * RabbitMQ broker.
  */
object MessagingUtil:
  /** The brokerConnection function creates a connection to the RabbitMQ broker.
    *
    * @param host
    *   the RabbitMQ host
    * @param port
    *   the RabbitMQ port
    * @param username
    *   the RabbitMQ username
    * @param password
    *   the RabbitMQ password
    *
    * @return
    *   a Resource monad with the RabbitMQ connection
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

  /** The channelFromConnection function creates a channel to the defined
    * RabbitMQ connection.
    *
    * @param connection
    *   the RabbitMQ connection
    *
    * @return
    *   a Resource monad with the RabbitMQ channel
    */
  def channelFromConnection(
      connection: Connection
  ): Resource[IO, Channel] =
    Resource.make(IO.delay(connection.createChannel()))(channel =>
      IO.delay(channel.close())
    )

  /** The channelWithExchange function creates an exchange on the defined
    * RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param exchangeName
    *   the name of the exchange
    * @param exchangeType
    *   the type of the exchange
    * @param durable
    *   the durability of the exchange
    * @param autoDelete
    *   the auto-deletion of the exchange
    * @param internal
    *   the internal flag of the exchange
    *
    * @return
    *   an IO monad with the operation result of creating the exchange
    */
  def channelWithExchange(
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

  /** The channelWithoutExchange function deletes an exchange on the defined
    * RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param exchangeName
    *   the name of the exchange
    * @param ifUnused
    *   the ifUnused flag
    *
    * @return
    *   an IO monad with the operation result of deleting the exchange
    */
  def channelWithoutExchange(
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

  /** The channelWithQueue function creates a queue on the defined RabbitMQ
    * channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param queueName
    *   the name of the queue
    * @param durable
    *   the durability of the queue
    * @param exclusive
    *   the exclusivity of the queue
    * @param autoDelete
    *   the auto-deletion of the queue
    *
    * @return
    *   an IO monad with the operation result of creating the queue
    */
  def channelWithQueue(
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

  /** The channelWithoutQueue function deletes a queue on the defined RabbitMQ
    * channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param queueName
    *   the name of the queue
    * @param ifUsed
    *   the ifUsed flag
    * @param ifEmpty
    *   the ifEmpty flag
    *
    * @return
    *   an IO monad with the operation result of deleting the queue
    */
  def channelWithoutQueue(
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

  /** The bindedQueueWithExchange function binds a queue to an exchange on the
    * defined RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param queueName
    *   the name of the queue
    * @param exchangeName
    *   the name of the exchange
    * @param routingKey
    *   the routing key
    *
    * @return
    *   an IO monad with the operation result of binding the queue to the
    *   exchange
    */
  def bindedQueueWithExchange(
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

  /** The bindedExchangeWithExchange function binds an exchange to another
    * exchange on the defined RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param sourceExchangeName
    *   the name of the source exchange
    * @param destinationExchangeName
    *   the name of the destination exchange
    * @param routingKey
    *   the routing key
    *
    * @return
    *   an IO monad with the operation result of binding the exchange to the
    *   other exchange
    */
  def bindedExchangeWithExchange(
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

  /** The channelWithQOS function sets the quality of service on the defined
    * RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param prefetchSize
    *   the prefetch size
    * @param prefetchCount
    *   the prefetch count
    * @param global
    *   the global flag
    *
    * @return
    *   an IO monad with the operation result of setting the quality of service
    */
  def channelWithQOS(
      channel: Channel,
      prefetchSize: Int = 0,
      prefetchCount: Int = 0,
      global: Boolean = false
  ): IO[Unit] =
    IO.delay(channel.basicQos(prefetchSize, prefetchCount, global))

  /** The channelWithPublisherConfirms function enables publisher confirms on
    * the defined RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    *
    * @return
    *   an IO monad with the operation result of enabling publisher confirms
    */

  def channelWithPublisherConfirms(channel: Channel): IO[Confirm.SelectOk] =
    IO.delay(channel.confirmSelect())

  /** The sendMessage function sends a message to an exchange on the defined
    * RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param exchangeName
    *   the name of the exchange
    * @param routingKey
    *   the routing key
    * @param messageBytes
    *   the message bytes
    * @param properties
    *   the message properties
    *
    * @return
    *   an IO monad with the operation result of sending the message
    */
  protected[messaging] def publishMessage(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      messageBytes: Array[Byte],
      properties: BasicProperties = null
  ): IO[Unit] =
    IO.delay(
      channel.basicPublish(
        exchangeName.value,
        routingKey.value,
        properties,
        messageBytes
      )
    )

  /** The consumeMessages function consumes messages from a queue on the defined
    * RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    * @param queueName
    *   the name of the queue
    * @param autoAck
    *   the auto-acknowledgement of the message
    * @param callback
    *   the message callback
    *
    * @return
    *   an IO monad with the operation result of consuming the messages
    */
  protected[messaging] def consumeMessages(
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

  /** The closeChannel function closes the RabbitMQ channel.
    *
    * @param channel
    *   the RabbitMQ channel
    *
    * @return
    *   an IO monad with the operation result of closing the channel
    */
  protected[messaging] def closeChannel(
      channel: Channel
  ): IO[Unit] =
    IO.delay(channel.close())

  /** The closeConnection function closes the RabbitMQ connection.
    *
    * @param connection
    *   the RabbitMQ connection
    *
    * @return
    *   an IO monad with the operation result of closing the connection
    */
  protected[messaging] def closeConnection(
      connection: Connection
  ): IO[Unit] =
    IO.delay(connection.close())
