package types

import actors.Orchestrator
import akka.actor.typed.ActorRef
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection

/** Represents the setup configuration for the MQManager.
  *
  * @param connection
  *   The RabbitMQ connection object
  * @param channel
  *   The RabbitMQ channel object
  * @param orchestratorRef
  *   The reference to the Orchestrator actor
  * @param mqConsumerRef
  *   The reference to the MQConsumer actor
  */
final case class MqManagerSetup(
    connection: Connection,
    channel: Channel,
    orchestratorRef: ActorRef[Orchestrator.Command],
    mqConsumerRef: ActorRef[Nothing]
)
