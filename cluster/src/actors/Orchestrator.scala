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

  // Private command protocol
  private final case class RegisterLog(task: Task, log: String) extends Command
  private final case class RegisterCrash(task: Task, reason: Throwable)
      extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    RemoteStorageManager.Response | MessageBrokerManager.Response

  def apply(appConfig: AppConfig): Behavior[CommandOrResponse] = setup(
    appConfig
  )

  private def setup(appConfig: AppConfig): Behavior[CommandOrResponse] =
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

      orchestrating(
        setup,
        MessageBrokerRoutingInfo(
          appConfig.logsExchange,
          appConfig.logsRoutingKey
        ),
        MessageBrokerRoutingInfo(
          appConfig.crashExchange,
          appConfig.crashRoutingKey
        )
      )
    }
  end setup

  private def orchestrating(
      setup: OrchestratorSetup,
      mqLogsRoutingInfo: MessageBrokerRoutingInfo,
      mqCrashRoutingInfo: MessageBrokerRoutingInfo
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ProcessTask(task) =>
            context.log.info(s"ProcessTask command received")
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
                mqLogsRoutingInfo.exchange,
                mqLogsRoutingInfo.routingKey
              ),
              PublishTarget.Logging
            )
            Behaviors.same

          case RegisterCrash(task, reason) =>
            val taskWithLog = task.copy(logMessage = Some(s"$reason"))

            context.log.info(
              s"RegisterCrash command received - Task --> $taskWithLog"
            )

            context.self ! RegisterLog(
              task,
              s"Task crashed with reason - $reason"
            )

            setup.messageQueueManager ! MessageBrokerManager.PublishTask(
              taskWithLog,
              MessageBrokerRoutingInfo(
                mqCrashRoutingInfo.exchange,
                mqCrashRoutingInfo.routingKey
              ),
              PublishTarget.Crashed
            )
            Behaviors.same

          /* **********************************************************************
           * Responses from RemoteStorageManager
           * ********************************************************************** */

          case RemoteStorageManager.TaskDownloaded(task) =>
            context.log.info(s"TaskDownloaded response received")
            context.self ! RegisterLog(
              task,
              "Task files donwloaded for processing"
            )

            setup.executionManager ! ExecutionManager.ExecuteTask(task)

            Behaviors.same

          case RemoteStorageManager.TaskUploaded(task) =>
            context.log.info(s"TaskUploaded response received")
            context.self ! RegisterLog(task, "Task execution files uploaded")

            setup.remoteStorageManager ! RemoteStorageManager.DeleteFiles(task)

            task.routingTree.fold {
              setup.messageQueueManager ! MessageBrokerManager.AckTask(
                task
              )
            } { routingNode =>
              setup.messageQueueManager ! MessageBrokerManager.PublishTask(
                task,
                MessageBrokerRoutingInfo(
                  routingNode.exchange,
                  routingNode.routingKey
                ),
                PublishTarget.Processing
              )
            }

            Behaviors.same

          case RemoteStorageManager.TaskDownloadFailed(task, reason) =>
            context.log.error(s"TaskDownloadFailed response received")

            context.self ! RegisterCrash(task, reason)

            context.self ! RegisterLog(
              task,
              s"Task files download failed with reason - $reason"
            )

            setup.messageQueueManager ! MessageBrokerManager.RejectTask(task)
            Behaviors.same

          case RemoteStorageManager.TaskUploadFailed(task, reason) =>
            context.log.error(s"TaskUploadFailed response received")

            context.self ! RegisterCrash(task, reason)

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
            context.log.info(s"TaskExecuted response received")
            context.self ! RegisterLog(
              task,
              "Task processing completed successfully"
            )

            setup.remoteStorageManager ! RemoteStorageManager.UploadTaskFiles(
              task.copy(routingTree =
                task.routingTree.flatMap(_.successRoutingDecision)
              )
            )

            Behaviors.same

          case ExecutionManager.TaskHalt(task) =>
            context.log.info(s"TaskHalt response received")
            context.self ! RegisterLog(
              task,
              "Task processing completed unsuccessfully"
            )

            setup.remoteStorageManager ! RemoteStorageManager.UploadTaskFiles(
              task.copy(routingTree =
                task.routingTree.flatMap(_.failureRoutingDecision)
              )
            )

            Behaviors.same

          case ExecutionManager.TaskExecutionError(task, reason) =>
            context.log.error(s"TaskExecutionError response received")

            context.self ! RegisterCrash(task, reason)

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
            context.log.info(s"TaskAcknowledged response received")

            context.self ! RegisterLog(
              task,
              "Task acknowledged to message broker"
            )

            Behaviors.same

          case MessageBrokerManager.TaskRejected(task) =>
            context.log.warn(s"TaskRejected response received")

            context.self ! RegisterLog(
              task,
              "Task rejected to message broker"
            )

            Behaviors.same

          case MessageBrokerManager.TaskPublishFailed(task, reason) =>
            context.log.error(s"TaskPublishFailed response received")
            Behaviors.same

          case MessageBrokerManager.TaskAckFailed(task, reason) =>
            context.log.error(s"TaskAckFailed response received")
            Behaviors.same

          case MessageBrokerManager.TaskRejectFailed(task, reason) =>
            context.log.error(s"TaskRejectFailed response received")
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
