package actors

import actors.execution.ExecutionManager
import actors.mq.MessageBrokerManager
import actors.mq.MessageBrokerManager.TaskAckFailed
import actors.mq.MessageBrokerManager.TaskPublishFailed
import actors.storage.RemoteStorageManager
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.ChildFailed
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.Terminated
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import types.AppConfig
import types.MessageBrokerRoutingInfo
import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.OrchestratorSetup
import types.PublishTarget
import types.Task

import scala.concurrent.duration.*

val MaxConsecutiveRestarts = 5

object Orchestrator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ProcessTask(task: Task) extends Command
  final case class RegisterLog(task: Task, log: String) extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    RemoteStorageManager.Response | MessageBrokerManager.Response

  def apply(appConfig: AppConfig): Behavior[CommandOrResponse] = setup(
    appConfig
  )

  def setup(appConfig: AppConfig): Behavior[CommandOrResponse] =
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
        .supervise(
          RemoteStorageManager(appConfig.remoteStorageConfig, context.self)
        )
        .onFailure(
          SupervisorStrategy.restart
            .withLimit(MaxConsecutiveRestarts, 5.minutes)
        )
      val rsManager = context.spawn(
        supervisedRsManager,
        "remote-storage-manager"
      )
      context.watch(rsManager)

      val supervisedMqManager = Behaviors
        .supervise(
          MessageBrokerManager(
            appConfig.messageBrokerConfig,
            appConfig.consumptionQueue,
            context.self
          )
        )
        .onFailure(
          SupervisorStrategy
            .restartWithBackoff(10.seconds, 5.minutes, 0.2)
            .withMaxRestarts(MaxConsecutiveRestarts)
        )
      val mqManager = context.spawn(
        supervisedMqManager,
        "message-broker-manager"
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

      orchestrating(setup, appConfig.logsExchange)
    }
  end setup

  def orchestrating(
      setup: OrchestratorSetup,
      mqLogsExchange: MessageBrokerExchange
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ProcessTask(task) =>
            context.log.info(
              s"ProcessTask command received"
            )
            setup.remoteStorageManager ! RemoteStorageManager
              .DownloadTaskFiles(
                task
              )
            context.self ! RegisterLog(
              task,
              "Task received for processing"
            )

            Behaviors.same

          case RegisterLog(task, log) =>
            val taskWithLog = task.copy(logMessage = Some(log))

            context.log.info(
              s"RegisterLog command received - Task --> $taskWithLog"
            )

            setup.messageQueueManager ! MessageBrokerManager.PublishTask(
              taskWithLog,
              MessageBrokerRoutingInfo(
                mqLogsExchange,
                MessageBrokerRoutingKey("task.log")
              ),
              PublishTarget.Reporting
            )
            Behaviors.same

          /* **********************************************************************
           * Responses from RemoteStorageManager
           * ********************************************************************** */

          case RemoteStorageManager.TaskDownloaded(task) =>
            context.log.info(
              s"TaskDownloaded response received"
            )
            context.self ! RegisterLog(
              task,
              "Task files donwloaded for processing"
            )

            setup.executionManager ! ExecutionManager.ExecuteTask(task)

            Behaviors.same

          case RemoteStorageManager.TaskUploaded(task) =>
            context.log.info(
              s"TaskUploaded response received"
            )
            context.self ! RegisterLog(task, "Task execution files uploaded")

            setup.remoteStorageManager ! RemoteStorageManager.DeleteFiles(task)

            val taskForNextStage =
              task.copy(routingKeys = task.routingKeys.drop(1))

            if taskForNextStage.routingKeys.nonEmpty then
              val (exchange, routingKey) = taskForNextStage.routingKeys.head
              setup.messageQueueManager ! MessageBrokerManager.PublishTask(
                taskForNextStage,
                MessageBrokerRoutingInfo(
                  exchange,
                  routingKey
                ),
                PublishTarget.Processing
              )
            else
              setup.messageQueueManager ! MessageBrokerManager.AckTask(
                taskForNextStage
              )
            end if

            Behaviors.same

          case RemoteStorageManager.TaskDownloadFailed(task, reason) =>
            context.log.error(
              s"TaskDownloadFailed response received"
            )

            context.self ! RegisterLog(
              task,
              s"Task files download failed with reason - $reason"
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          case RemoteStorageManager.TaskUploadFailed(task, reason) =>
            context.log.error(
              s"TaskUploadFailed response received"
            )

            setup.remoteStorageManager ! RemoteStorageManager.DeleteFiles(task)

            context.self ! RegisterLog(
              task,
              s"Task files upload failed with reason - $reason"
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          /* **********************************************************************
           * Responses from ExecutionManager
           * ********************************************************************** */

          case ExecutionManager.TaskPass(task) =>
            context.log.info(
              s"TaskExecuted response received"
            )
            context.self ! RegisterLog(
              task,
              "Task processing completed successfully"
            )

            setup.remoteStorageManager ! RemoteStorageManager
              .UploadTaskFiles(task)

            Behaviors.same

          case ExecutionManager.TaskHalt(task) =>
            context.log.info(
              s"TaskHalt response received"
            )
            context.self ! RegisterLog(
              task,
              "Task processing completed unsuccessfully"
            )

            val taskWithoutStages =
              task.copy(routingKeys = Nil)

            setup.remoteStorageManager ! RemoteStorageManager
              .UploadTaskFiles(taskWithoutStages)

            Behaviors.same

          case ExecutionManager.TaskExecutionError(task, reason) =>
            context.log.error(
              s"TaskExecutionError response received"
            )

            setup.remoteStorageManager ! RemoteStorageManager.DeleteFiles(task)

            context.self ! RegisterLog(
              task,
              s"Task execution failed with reason - $reason"
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          /* **********************************************************************
           * Responses from MessageBrokerManager
           * ********************************************************************** */

          case MessageBrokerManager.TaskPublished(task, publishTarget) =>
            context.log.info(
              s"TaskPublished ($publishTarget) response received"
            )

            if publishTarget == PublishTarget.Processing then
              context.self ! RegisterLog(
                task,
                "Task sent for next processing stage"
              )
              setup.messageQueueManager ! MessageBrokerManager.AckTask(task)
            end if

            Behaviors.same

          case MessageBrokerManager.TaskAcknowledged(task) =>
            context.log.info(
              s"TaskAcknowledged response received"
            )

            context.self ! RegisterLog(
              task,
              "Task acknowledged to message broker"
            )

            Behaviors.same

          case MessageBrokerManager.TaskRejected(task) =>
            context.log.warn(
              s"TaskRejected response received"
            )

            context.self ! RegisterLog(
              task,
              "Task rejected to message broker"
            )

            Behaviors.same

          case MessageBrokerManager.TaskPublishFailed(task, reason) =>
            context.log.error(
              s"TaskPublishFailed response received"
            )

            Behaviors.same

          case MessageBrokerManager.TaskAckFailed(task, reason) =>
            context.log.error(
              s"TaskAckFailed response received"
            )

            Behaviors.same

          case MessageBrokerManager.TaskRejectFailed(task, reason) =>
            context.log.error(
              s"TaskRejectFailed response received"
            )

            Behaviors.same

        end match
      }
      .receiveSignal {
        case (context, ChildFailed(ref, reason)) =>
          context.log.error(s"Reference $ref failed with reason - $reason")
          Behaviors.stopped

        case (context, Terminated(ref)) =>
          context.log.info(s"Reference $ref terminated")
          Behaviors.same

        case (context, PostStop) =>
          context.log.warn("PostStop signal")
          context.stop(setup.messageQueueManager)
          context.stop(setup.executionManager)
          context.stop(setup.remoteStorageManager)
          context.stop(setup.systemMonitor)
          Behaviors.same
      }
  end orchestrating

end Orchestrator
