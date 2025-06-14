package actors.mq

/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import actors.Orchestrator
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import types.MqMessage
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.MqHost
import types.OpaqueTypes.MqPassword
import types.OpaqueTypes.MqPort
import types.OpaqueTypes.MqUser
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey
import types.Task

private val DefaultMqRetries = 10

/**
 * This actor manages the actors that are related to the Message Queue. It acts as the intermediary
 * between the MQ and the system that processes the tasks that come from the MQ.
 */
object MqManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class MqProcessTask(mqMessage: MqMessage) extends Command
  final case class MqAcknowledgeTask(
    id: Long,
    retries: Int = DefaultMqRetries,
  ) extends Command
  final case class MqRejectTask(id: Long, retries: Int = DefaultMqRetries) extends Command
  final case class MqSendMessage(
    task: Task,
    exchange: ExchangeName,
    routingKey: RoutingKey,
    retries: Int = DefaultMqRetries,
  ) extends Command
  final case class MqSetQosPrefetchCount(prefetchCount: Int) extends Command
  case object GracefulShutdown extends Command

  // Internal command protocol
  private final case class DeliverToOrchestrator(task: Task) extends Command
  private case object NoOp extends Command

  // Response Protocol
  sealed trait Response

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(
    mqHost: MqHost,
    mqPort: MqPort,
    mqUser: MqUser,
    mqPass: MqPassword,
    consumptionQueue: QueueName,
    maxPrefetchCount: Int,
    replyTo: ActorRef[Orchestrator.Command],
  ): Behavior[Command] =
    setup(
      mqHost,
      mqPort,
      mqUser,
      mqPass,
      consumptionQueue,
      maxPrefetchCount,
      replyTo,
    )

  /**
   * This behavior sets up the MqManager actor and then proceeds to process the messages that are
   * sent to it.
   *
   * @param mqHost
   *   hostname of the MQ
   * @param mqPort
   *   port of the MQ
   * @param mqUser
   *   username to connect to the MQ
   * @param mqPass
   *   password to connect to the MQ
   * @param consumptionQueue
   *   name of the queue to consume messages from
   * @param replyTo
   *   reference to the Orchestrator actor
   *
   * @return
   *   a behavior that processes the messages sent to the MqManager
   */
  def setup(
    mqHost: MqHost,
    mqPort: MqPort,
    mqUser: MqUser,
    mqPass: MqPassword,
    consumptionQueue: QueueName,
    maxPrefetchCount: Int,
    replyTo: ActorRef[Orchestrator.Command],
  ): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.info("MqManager started...")

      // Create connection and channel to the broker to be handled by the MqManager globally

      brokerConnecton(
        mqHost.value,
        mqPort.value,
        mqUser.value,
        mqPass.value,
      ) match
        case Success(connection) =>
          context.log.info(
            s"Connection to broker established. Host --> ${mqHost.value}, Port --> ${mqPort.value}.",
          )

          val channel = connection.createChannel

          val _ =
            setQosPrefetchCount(channel, maxPrefetchCount) // TODO review this

          // Spawn the MqConsumer actor to be able to consume messages from the MQ
          val _ = context.spawn(
            MqConsumer(channel, consumptionQueue, context.self),
            "mq-consumer",
          )

          Behaviors.receiveMessage[Command] { message =>
            message match

              /* **********************************************************************
               * Public commands
               * ********************************************************************** */

              case MqProcessTask(mqMessage) =>
                context.log.info(
                  s"MqProcessTask command received. MqMessage --> $mqMessage.",
                )
                context.log.info(s"Spawning deserializer...")
                val deserializer = context.spawnAnonymous(MqMessageConverter())

                context.log.info(s"Deserializer spawned.")
                context.log.info(
                  s"Sending mqMessage to deserializer... MqMessage --> $mqMessage",
                )

                context
                  .askWithStatus[MqMessageConverter.DeserializeMessage, Task](
                    deserializer,
                    replyTo => MqMessageConverter.DeserializeMessage(mqMessage, replyTo),
                  ) {
                    case Success(task) =>
                      context.log.info(
                        s"Deserialization success response received from deserializer. Task awaiting rerouting to orchestrator. Task --> $task.",
                      )
                      DeliverToOrchestrator(task)

                    case Failure(exception) =>
                      context.log.info(
                        s"Deserialization failure response received from deserializer. Message awaiting rejection to MQ. MqMessage --> $mqMessage. Exception thrown: ${exception.getMessage()}",
                      )
                      MqRejectTask(mqMessage.mqId)
                  }
                Behaviors.same

              case MqAcknowledgeTask(mqId, retries) =>
                context.log.info(
                  s"MqAcknowledgeTask command received. mqId --> $mqId.",
                )
                context.log.info(s"Spawning communicator...")
                val communicator =
                  context.spawnAnonymous(MqCommunicator(channel))

                context.log.info(s"Communicator spawned.")
                context.log.info(
                  s"Sending mqId to communicator. mqId --> $mqId",
                )

                context.askWithStatus[MqCommunicator.SendAck, Done](
                  communicator,
                  replyTo => MqCommunicator.SendAck(mqId, replyTo),
                ) {
                  case Success(Done) =>
                    context.log.info(
                      s"MQ Ack success response received from communicator. mqId --> $mqId.",
                    )
                    NoOp

                  case Failure(exception) =>
                    val failureMessage =
                      s"MQ Ack failure response received from communicator. mqId --> $mqId. Exception thrown: ${exception
                          .getMessage()}. $retries retires left."

                    if retries > 0 then
                      context.log.warn(s"$failureMessage Retrying...")
                      MqAcknowledgeTask(mqId, retries - 1)
                    else
                      context.log.error(s"$failureMessage Retries exhausted.")
                      NoOp
                    end if
                }
                Behaviors.same

              case MqRejectTask(mqId, retries) =>
                context.log.info(
                  s"MqRejectTask command received. mqId --> $mqId.",
                )
                context.log.info(s"Spawning communicator...")
                val communicator =
                  context.spawnAnonymous(MqCommunicator(channel))

                context.log.info(s"Communicator spawned.")
                context.log.info(
                  s"Sending mqId to communicator. mqId --> $mqId",
                )

                context.askWithStatus[MqCommunicator.SendReject, Done](
                  communicator,
                  replyTo => MqCommunicator.SendReject(mqId, replyTo),
                ) {
                  case Success(Done) =>
                    context.log.info(
                      s"Reject success response received from communicator. mqId --> $mqId.",
                    )
                    NoOp

                  case Failure(exception) =>
                    val failureMessage =
                      s"Reject failure response received from communicator. mqId --> $mqId. Exception thrown: ${exception
                          .getMessage()}. $retries retires left."

                    if retries > 0 then
                      context.log.warn(s"$failureMessage Retrying...")
                      MqRejectTask(mqId, retries - 1)
                    else
                      context.log.error(s"$failureMessage Retries exhausted.")
                      NoOp
                    end if
                }
                Behaviors.same

              case MqSendMessage(task, exchange, routingKey, retries) =>
                context.log.info(
                  s"MqSendMessage command received. Task --> $task, exchange --> ${exchange.value}, routingKey --> ${routingKey.value}.",
                )
                context.log.info(s"Spawning serializer...")
                val serializer = context.spawnAnonymous(MqMessageConverter())

                context.log.info(s"Serializer spawned.")
                context.log.info(
                  s"Sending task to serializer. Task --> $task.",
                )

                context
                  .askWithStatus[MqMessageConverter.SerializeMessage, Seq[
                    Byte,
                  ]](
                    serializer,
                    replyTo => MqMessageConverter.SerializeMessage(task, replyTo),
                  ) {
                    case Success(bytes) =>
                      context.log.info(
                        s"Serialization success response received from serializer. Message awaiting delivery to MQ. Task --> $task.",
                      )

                      context.log.info(s"Spawning communicator...")
                      val communicator =
                        context.spawnAnonymous(MqCommunicator(channel))

                      context.log.info(s"Communicator spawned.")
                      context.log.info(
                        s"Sending message to communicator. Task --> $task, exchange --> ${exchange.value}, routingKey --> ${routingKey.value}.",
                      )

                      context.askWithStatus[MqCommunicator.SendMqMessage, Done](
                        communicator,
                        replyTo =>
                          MqCommunicator
                            .SendMqMessage(bytes, exchange, routingKey, replyTo),
                      ) {
                        case Success(Done) =>
                          context.log.info(
                            s"Send message success response received from communicator. Task --> $task, exchange --> ${exchange.value}, routingKey --> ${routingKey.value}.",
                          )
                          NoOp

                        case Failure(exception) =>
                          val failureMessage =
                            s"Send message failure response received from communicator. Task --> $task, exchange --> ${exchange.value}, routingKey --> ${routingKey.value}. Exception thrown: ${exception
                                .getMessage()}. $retries retires left."

                          if retries > 0 then
                            context.log.warn(s"$failureMessage Retrying...")
                            MqSendMessage(
                              task,
                              exchange,
                              routingKey,
                              retries - 1,
                            )
                          else
                            context.log.error(
                              s"$failureMessage Retries exhausted." +
                                s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
                                s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
                                s"\nCONTACT SYSTEM ADMINISTRATOR!!!.",
                            )
                            NoOp
                          end if
                      }

                      NoOp
                    case Failure(exception) =>
                      context.log.error(
                        s"Serialization failure response received from serializer. Task --> $task. Exception thrown: ${exception
                            .getMessage()}." +
                          s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
                          s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
                          s"\nCONTACT SYSTEM ADMINISTRATOR!!!.",
                      )
                      NoOp
                  }
                Behaviors.same

              case MqSetQosPrefetchCount(prefetchCount) =>
                context.log.info(
                  s"MqSetQosPrefetchCount command received. Setting Qos prefetch count to $prefetchCount.",
                )

                setQosPrefetchCount(channel, prefetchCount) match
                  case Success(_) =>
                    context.log.info(
                      s"Qos set successfully to $prefetchCount.",
                    )
                  case Failure(exception) =>
                    context.log.error(
                      s"Qos set failure. Exception thrown: ${exception.getMessage()}",
                    )
                end match

                Behaviors.same

              /* **********************************************************************
               * Internal commands
               * ********************************************************************** */

              case DeliverToOrchestrator(task) =>
                context.log.info(
                  s"DeliverToOrchestrator command received. Task --> $task.",
                )
                context.log.info(
                  s"Sending task to orchestrator... Task --> $task.",
                )
                replyTo ! Orchestrator.ProcessTask(task)
                Behaviors.same

              case NoOp =>
                Behaviors.same

              /* **********************************************************************
               * Shutdown command
               * ********************************************************************** */

              case GracefulShutdown =>
                context.log.info(
                  s"GracefulShutdown command received. Closing channel and connection to broker.",
                )
                channel.close()
                connection.close()
                Behaviors.stopped

          }

        case Failure(exception) =>
          context.log.error(
            s"Connection to broker failed. Host --> ${mqHost.value}, Port --> ${mqPort.value}. Exception thrown: ${exception.getMessage()}",
          )

          context.log.error(
            s"Shutting down MqManager. CONTACT SYSTEM ADMINISTRATOR!!!." +
              s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
              s"\nCONTACT SYSTEM ADMINISTRATOR!!!.",
          )

          replyTo ! Orchestrator.Fail(
            "Connection to broker failed. CONTACT SYSTEM ADMINISTRATOR!!!.",
          )
          Behaviors.stopped
      end match
    }

  def brokerConnecton(
    host: String,
    port: Int,
    user: String,
    pass: String,
  ): Try[Connection] =
    val factory = ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(user)
    factory.setPassword(pass)
    Try(factory.newConnection())

  end brokerConnecton

  def setQosPrefetchCount(channel: Channel, prefetchCount: Int): Try[Unit] =
    // Since it is not possible to change the prefetch count of a single consumer
    // in RabbitMQ, we set the global prefetch count to the desired value.
    // This enables the channel to have a dynamically adjustable prefetch count
    // regardless of the number of consumers.
    Try(channel.basicQos(prefetchCount, true))

end MqManager
