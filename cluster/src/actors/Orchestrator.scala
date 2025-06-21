package actors

import scala.concurrent.duration.*

import actors.execution.ExecutionManager
import actors.mq.MessageBrokerManager
import actors.mq.MessageBrokerManager.TaskAckFailed
import actors.mq.MessageBrokerManager.TaskPublishFailed
import actors.storage.RemoteStorageManager
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.ChildFailed
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import types.MessageQueueConnectionParams
import types.OpaqueTypes.MessageBrokerExchangeName
import types.OpaqueTypes.MessageBrokerQueueName
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.OrchestratorSetup
import types.RemoteStorageConnectionParams
import types.Task

val MaxConsecutiveRestarts = 5

object Orchestrator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ProcessTask(task: Task) extends Command
  final case class RegisterLog(task: Task, log: String) extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    RemoteStorageManager.Response | MessageBrokerManager.Response

  def apply(
      mqExchangeName: MessageBrokerExchangeName,
      mqQueueName: MessageBrokerQueueName,
      mqConnParams: MessageQueueConnectionParams,
      rsConnParams: RemoteStorageConnectionParams
  ): Behavior[CommandOrResponse] = setup(
    mqExchangeName,
    mqQueueName,
    mqConnParams,
    rsConnParams
  )

  def setup(
      mqExchangeName: MessageBrokerExchangeName,
      mqQueueName: MessageBrokerQueueName,
      mqConnParams: MessageQueueConnectionParams,
      rsConnParams: RemoteStorageConnectionParams
  ): Behavior[CommandOrResponse] =
    Behaviors.setup[CommandOrResponse] { context =>
      context.log.info("Orchestrator started...")

      val supervisedExecutionManager = Behaviors
        .supervise(ExecutionManager(context.self))
        .onFailure(SupervisorStrategy.resume)
      val executionManager = context.spawn(
        supervisedExecutionManager,
        "execution-manager"
      )
      context.watch(executionManager)

      val supervisedRsManager = Behaviors
        .supervise(RemoteStorageManager(rsConnParams, context.self))
        .onFailure(
          SupervisorStrategy.restart
            .withLimit(MaxConsecutiveRestarts, 5.minutes)
        )
      val rsManager = context.spawn(
        supervisedRsManager,
        "ftp-manager"
      )
      context.watch(rsManager)

      val supervisedMqManager = Behaviors
        .supervise(
          MessageBrokerManager(mqConnParams, mqQueueName, context.self)
        )
        .onFailure(
          SupervisorStrategy
            .restartWithBackoff(1.seconds, 5.seconds, 0.2)
            .withMaxRestarts(MaxConsecutiveRestarts)
        )
      val mqManager = context.spawn(
        supervisedMqManager,
        "mq-manager"
      )
      context.watch(mqManager)

      val supervisedSystemMonitor = Behaviors
        .supervise(SystemMonitor(context.self))
        .onFailure(
          SupervisorStrategy.restart.withLimit(MaxConsecutiveRestarts, 1.minute)
        )
      val systemMonitor = context.spawn(
        supervisedSystemMonitor,
        "system-monitor"
      )
      context.watch(systemMonitor)

      val setup = OrchestratorSetup(
        mqManager,
        rsManager,
        executionManager,
        systemMonitor
      )

      orchestrating(setup, mqExchangeName)
    }
  end setup

  def orchestrating(
      setup: OrchestratorSetup,
      mqExchangeName: MessageBrokerExchangeName
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ProcessTask(task) =>
            context.log.info(
              s"ProcessTask command received. Task --> $task."
            )
            setup.remoteStorageManager ! RemoteStorageManager
              .DownloadTaskFiles(
                task
              )
            context.self ! RegisterLog(
              task,
              "Task received for processing."
            )

            Behaviors.same

          case RegisterLog(task, log) =>
            val taskWithLog = task.copy(logMessage = Some(log))

            context.log.info(
              s"RegisterLog command received. Task --> $taskWithLog."
            )

            setup.messageQueueManager ! MessageBrokerManager.PublishTask(
              taskWithLog,
              mqExchangeName,
              MessageBrokerRoutingKey("updates")
            )
            Behaviors.same

          /* **********************************************************************
           * Responses from RemoteStorageManager
           * ********************************************************************** */

          case RemoteStorageManager.TaskDownloaded(task) =>
            context.log.info(
              s"TaskDownloaded response received. Task --> $task."
            )
            context.self ! RegisterLog(
              task,
              "Task files donwloaded for processing."
            )

            setup.executionManager ! ExecutionManager.ExecuteTask(task)

            Behaviors.same

          case RemoteStorageManager.TaskUploaded(task) =>
            context.log.info(
              s"TaskUploaded response received. Task --> $task."
            )
            context.self ! RegisterLog(task, "Task execution files uploaded.")

            if task.routingKeys.tail.nonEmpty then
              val taskForNextStage = task.copy(
                routingKeys = task.routingKeys.tail
              )

              setup.messageQueueManager ! MessageBrokerManager.PublishTask(
                taskForNextStage,
                mqExchangeName,
                MessageBrokerRoutingKey(taskForNextStage.routingKeys.head)
              )
            else setup.messageQueueManager ! MessageBrokerManager.AckTask(task)
            end if

            Behaviors.same

          case RemoteStorageManager.TaskDownloadFailed(task, reason) =>
            context.log.info(
              s"TaskDownloadFailed response received. Task --> $task."
            )

            context.self ! RegisterLog(
              task,
              s"Task files download failed with reason - ${reason.getMessage()}."
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          case RemoteStorageManager.TaskUploadFailed(task, reason) =>
            context.log.info(
              s"TaskUploadFailed response received. Task --> $task."
            )

            context.self ! RegisterLog(
              task,
              s"Task files upload failed with reason - ${reason.getMessage()}."
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          /* **********************************************************************
           * Responses from ExecutionManager
           * ********************************************************************** */

          case ExecutionManager.TaskPass(task) =>
            context.log.info(
              s"TaskExecuted response received. Task --> $task."
            )
            context.self ! RegisterLog(
              task,
              "Task processing completed successfully."
            )

            setup.remoteStorageManager ! RemoteStorageManager
              .UploadTaskFiles(task)

            Behaviors.same

          case ExecutionManager.TaskHalt(task) =>
            context.log.info(
              s"TaskHalt response received. Task --> $task."
            )
            context.self ! RegisterLog(
              task,
              "Task processing completed unsuccessfully."
            )

            val taskWithoutStages = task.copy(routingKeys = List.empty[String])

            setup.remoteStorageManager ! RemoteStorageManager
              .UploadTaskFiles(taskWithoutStages)

            Behaviors.same

          case ExecutionManager.TaskExecutionError(task, reason) =>
            context.log.info(
              s"TaskExecutionError response received. Task --> $task."
            )

            context.self ! RegisterLog(
              task,
              s"Task execution failed with reason - ${reason.getMessage()}"
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          /* **********************************************************************
           * Responses from MqManager
           * ********************************************************************** */

          case MessageBrokerManager.TaskPublished(task) =>
            context.log.info(
              s"TaskPublished response received. Task --> $task."
            )

            context.self ! RegisterLog(
              task,
              "Task sent for next processing stage."
            )

            setup.messageQueueManager ! MessageBrokerManager.AckTask(task)

            Behaviors.same

          case MessageBrokerManager.TaskAcknowledged(task) =>
            context.log.info(
              s"TaskAcknowledged response received. Task --> $task."
            )

            context.self ! RegisterLog(
              task,
              "Task acknowledged by message broker."
            )

            Behaviors.same

          case MessageBrokerManager.TaskRejected(task) =>
            context.log.info(
              s"TaskRejected response received. Task --> $task."
            )

            context.self ! RegisterLog(
              task,
              "Task rejected by message broker."
            )

            Behaviors.same

          case MessageBrokerManager.TaskPublishFailed(task, reason) =>
            context.log.error(
              s"TaskPublishFailed response received. Task --> $task."
            )

            // context.self ! RegisterLog(
            //   task,
            //   "Task publish failed."
            // )

            Behaviors.same

          case MessageBrokerManager.TaskAckFailed(task, reason) =>
            context.log.error(
              s"TaskAckFailed response received. Task --> $task."
            )

            // context.self ! RegisterLog(
            //   task,
            //   "Task acknowledgement failed."
            // )

            Behaviors.same

          case MessageBrokerManager.TaskRejectFailed(task, reason) =>
            context.log.error(
              s"TaskRejectFailed response received. Task --> $task."
            )

            // context.self ! RegisterLog(
            //   task,
            //   "Task rejection failed."
            // )

            Behaviors.same

        end match
      }
      .receiveSignal { case (context, ChildFailed(ref)) =>
        context.log.error(s"Child actor failed: $ref")
        Behaviors.same
      }
  end orchestrating

end Orchestrator
