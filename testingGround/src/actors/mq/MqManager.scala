package actors.mq

/** @author
  *   Esteban Gonzalez Ruales
  */

import actors.Orchestrator
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.util.Timeout
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

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

private val DefaultMqRetries = 10

/** This actor manages the actors that are related to the Message Queue. It acts
  * as the intermediary between the MQ and the system that processes the tasks
  * that come from the MQ.
  */
object MqManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class MqProcessTask(mqMessage: MqMessage) extends Command
  final case class MqAcknowledgeTask(
      id: String,
      retries: Int = DefaultMqRetries
  ) extends Command
  final case class MqRejectTask(id: String, retries: Int = DefaultMqRetries)
      extends Command
  final case class MqSendMessage(
      task: Task,
      exchange: ExchangeName,
      routingKey: RoutingKey,
      retries: Int = DefaultMqRetries
  ) extends Command
  case object Shutdown extends Command

  // Internal command protocol
  private final case class DeliverToOrchestrator(task: Task) extends Command
  private final case class Report(message: String) extends Command

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(
      replyTo: ActorRef[Orchestrator.Command],
      mqHost: MqHost,
      mqPort: MqPort,
      mqUser: MqUser,
      mqPass: MqPassword,
      consumptionQueue: QueueName
  ): Behavior[Command] =
    setup(replyTo, mqHost, mqPort, mqUser, mqPass, consumptionQueue)

  /** This behavior sets up the MqManager actor and then proceeds to process the
    * messages that are sent to it.
    *
    * @param replyTo
    *   reference to the Orchestrator actor
    * @param mqHost
    *   hostname of the MQ
    * @param mqPort
    *   port of the MQ
    * @param mqUser
    *   username to connect to the MQ
    * @param mqPass
    *   password to connect to the MQ
    *
    * @return
    *   a behavior that processes the messages sent to the MqManager
    */
  def setup(
      replyTo: ActorRef[Orchestrator.Command],
      mqHost: MqHost,
      mqPort: MqPort,
      mqUser: MqUser,
      mqPass: MqPassword,
      consumptionQueue: QueueName
  ): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.info("MqManager started...")

      // Create connection and channel to the broker to be handled by the MqManager globally
      val connection =
        brokerConnecton(mqHost.value, mqPort.value, mqUser.value, mqPass.value)
      val channel = connection.createChannel

      // Spawn the MqConsumer actor to be able to consume messages from the MQ
      val mqConsumer = context.spawn(
        MqConsumer(context.self, channel, consumptionQueue),
        "mq-consumer"
      )

      /** This behavior is the main loop that processes the messages that are
        * sent to the MqManager.
        *
        * @return
        *   a behavior that processes the messages sent to the MqManager
        */
      def processingMessages(): Behavior[Command] =
        Behaviors.receiveMessage[Command] { message =>
          message match

            /* **********************************************************************
             * Public commands
             * ********************************************************************** */

            /* MqProcessTask
             *
             * This command is sent by the MqConsumer actor when it receives a new message to process.
             * It attempts to deserialize the message and then sends it to the orchestrator. In case of failure it rejects the message to the MQ.
             *
             */

            case MqProcessTask(mqMessage) =>
              context.log.info(
                s"MqManager received MQ message with id: ${mqMessage.id}. Attempting to spawn deserializer..."
              )
              val deserializer = context.spawnAnonymous(MqMessageConverter())

              context
                .askWithStatus[MqMessageConverter.DeserializeMessage, Task](
                  deserializer,
                  replyTo =>
                    MqMessageConverter.DeserializeMessage(mqMessage, replyTo)
                ) {
                  case Success(task) =>
                    context.log.info(
                      s"MqManager received deserialized message as task with id: ${task.mqId}. Task awaiting rerouting to orchestrator."
                    )
                    DeliverToOrchestrator(task)

                  case Failure(exception) =>
                    context.log.error(
                      s"MqManager received failure deserializing message with id: ${mqMessage.id} with error: ${exception.getMessage()}. Task awaiting rejection from MQ."
                    )
                    MqRejectTask(mqMessage.id)
                }
              Behaviors.same

            /* MqAcknowledgeTask
             *
             * This command is sent by the Orchestrator actor when it has successfully processed a task. It attempts to acknowledge the task to the MQ.
             * In case of failure it retries the operation until the retries are exhausted since there is nothing else to do.
             */

            case MqAcknowledgeTask(mqId, retries) =>
              context.log.info(
                s"MqManager attempting to swpawn communicator to ack task with id: $mqId..."
              )
              val communicator = context.spawnAnonymous(MqCommunicator(channel))

              context.askWithStatus[MqCommunicator.SendAck, Done](
                communicator,
                replyTo => MqCommunicator.SendAck(mqId, replyTo)
              ) {
                case Success(Done) =>
                  context.log.info(
                    s"MqManager received ack success for task with id: $mqId."
                  )
                  Report("Ack successful")

                case Failure(exception) =>
                  if retries > 0 then
                    context.log.error(
                      s"MqManager received ack failure for task with id: $mqId with error: ${exception.getMessage()}. Retrying..."
                    )
                    MqAcknowledgeTask(mqId, retries - 1)
                  else
                    context.log.error(
                      s"MqManager received ack failure for task with id: $mqId with error: ${exception.getMessage()}. Retries exhausted."
                    )
                    Report("Ack failed")
              }
              Behaviors.same

            /* MqRejectTask
             *
             * This command is sent by the Orchestrator actor when it has failed to process a task on any part of the process. It attempts to reject the task to the MQ.
             * In case of failure it retries the operation until the retries are exhausted since there is nothing else to do.
             */

            case MqRejectTask(mqId, retries) =>
              context.log.info(
                s"MqManager attempting to spawn communicator to reject task with id: $mqId..."
              )
              val communicator = context.spawnAnonymous(MqCommunicator(channel))

              context.askWithStatus[MqCommunicator.SendReject, Done](
                communicator,
                replyTo => MqCommunicator.SendReject(mqId, replyTo)
              ) {
                case Success(Done) =>
                  context.log.info(
                    s"MqManager received reject success for task with id $mqId."
                  )
                  Report("Reject successful")

                case Failure(exception) =>
                  if retries > 0 then
                    context.log.error(
                      s"MqManager received reject failure for task with id $mqId with error: ${exception.getMessage()}. Retrying..."
                    )
                    MqRejectTask(mqId, retries - 1)
                  else
                    context.log.error(
                      s"MqManager received reject failure for task with id $mqId with error: ${exception.getMessage()}. Retries exhausted."
                    )
                    Report("Reject failed")
              }
              Behaviors.same

            /* MqSendMessage
             *
             * This command is sent by the Orchestrator actor when it has a task to send to the MQ. It attempts to serialize the message and then send it to the MQ.
             * In case of failure it retries the operation until the retries are exhausted since there is nothing else to do.
             */

            case MqSendMessage(task, exchange, routingKey, retries) =>
              context.log.info(
                s"MqManager received task to send to MQ with id: ${task.taskId}, exchange: ${exchange.value}, routing key: ${routingKey.value}. Attempting to spawn serializer..."
              )
              val serializer = context.spawnAnonymous(MqMessageConverter())

              context
                .askWithStatus[MqMessageConverter.SerializeMessage, Seq[Byte]](
                  serializer,
                  replyTo => MqMessageConverter.SerializeMessage(task, replyTo)
                ) {
                  case Success(bytes) =>
                    context.log.info(
                      s"MqManager received success serializing message with id: ${task.taskId}. Attempting to spawn communicator..."
                    )

                    val communicator =
                      context.spawnAnonymous(MqCommunicator(channel))

                    context.askWithStatus[MqCommunicator.SendMqMessage, Done](
                      communicator,
                      replyTo =>
                        MqCommunicator
                          .SendMqMessage(bytes, exchange, routingKey, replyTo)
                    ) {
                      case Success(Done) =>
                        context.log.info(
                          s"MqManager received success sending message with id: ${task.taskId} to exchange: ${exchange.value}, routing key: ${routingKey.value}."
                        )
                        Report("Message sent successfully")

                      case Failure(exception) =>
                        if retries > 0 then
                          context.log.error(
                            s"MqManager received failure sending message with id: ${task.taskId} to exchange: ${exchange.value}, routing key: ${routingKey.value} with error: ${exception.getMessage()}. Retrying..."
                          )
                          MqSendMessage(
                            task,
                            exchange,
                            routingKey,
                            retries - 1
                          )
                        else
                          context.log.error(
                            s"MqManager received failure sending message with id: ${task.taskId} to exchange: ${exchange.value}, routing key: ${routingKey.value} with error: ${exception.getMessage()}. Retries exhausted."
                          )
                          Report(exception.getMessage)
                    }

                    Report("Message serialized successfully")
                  case Failure(exception) =>
                    if retries > 0 then
                      context.log.error(
                        s"MqManager received failure serializing message with id: ${task.taskId}. Retrying..."
                      )

                      MqSendMessage(
                        task,
                        exchange,
                        routingKey,
                        retries - 1
                      )
                    else
                      context.log.error(
                        s"MqManager received failure serializing message with id: ${task.taskId}. Retries exhausted."
                      )
                      Report(exception.getMessage)
                }

              Behaviors.same

            /* Shutdown
             *
             * This command is sent by the system when it is shutting down. It closes the channel and connection to the MQ and stops the MqManager actor.
             */

            case Shutdown =>
              context.log.info("Shutting down MqManager...")
              channel.close()
              connection.close()
              Behaviors.stopped

            /* **********************************************************************
             * Internal commands
             * ********************************************************************** */

            /* DeliverToOrchestrator
             *
             * This command is sent by the MqProcessTask command when it has successfully deserialized a message. It sends the task to the Orchestrator actor to be processed.
             */

            case DeliverToOrchestrator(task) =>
              context.log.info(
                s"MqManager received task with id: ${task.taskId}. Delivering to orchestrator..."
              )
              replyTo ! Orchestrator.ProcessTask(task)
              Behaviors.same

            /* Report
             *
             * This command is sent by the public commands when they have finished their operation. It logs the message and continues processing.
             */

            case Report(message) =>
              context.log.info(s"MqManager received report: $message")
              Behaviors.same
        }

      processingMessages()
    }

  def brokerConnecton(
      host: String,
      port: Int,
      user: String,
      pass: String
  ): Connection =
    val factory = ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(user)
    factory.setPassword(pass)
    println("Connecting to broker...")
    factory.newConnection()

  end brokerConnecton

end MqManager
