package actors

import actors.execution.ExecutionManager
import actors.mq.MqManager
import actors.storage.RemoteStorageManager
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import types.MessageQueueConnectionParams
import types.OpaqueTypes.MqExchangeName
import types.OpaqueTypes.MqQueueName
import types.OpaqueTypes.RoutingKey
import types.OrchestratorSetup
import types.RemoteStorageConnectionParams
import types.Task

object Orchestrator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ProcessTask(task: Task) extends Command
  final case class RegisterLog(task: Task, log: String) extends Command

  // Internal command protocol
  final case class GeneralAcknowledgeTask(task: Task) extends Command
  final case class GeneralRejectTask(task: Task) extends Command
  final case class GracefulShutdown(reason: String) extends Command
  final case class Fail(th: Throwable) extends Command

  private type CommandOrResponse =
    Command | ExecutionManager.Response |
      RemoteStorageManager.Response // | MqManager.Response

  def apply(
      mqExchangeName: MqExchangeName,
      mqQueueName: MqQueueName,
      mqConnParams: MessageQueueConnectionParams,
      rsConnParams: RemoteStorageConnectionParams
  ): Behavior[CommandOrResponse] = setup(
    mqExchangeName,
    mqQueueName,
    mqConnParams,
    rsConnParams
  )

  def setup(
      mqExchangeName: MqExchangeName,
      mqQueueName: MqQueueName,
      mqConnParams: MessageQueueConnectionParams,
      rsConnParams: RemoteStorageConnectionParams
  ): Behavior[CommandOrResponse] =
    Behaviors.setup[CommandOrResponse] { context =>
      context.log.info("Orchestrator started...")

      val executionManager = context.spawn(
        ExecutionManager(context.self),
        "processing-manager"
      )

      val remoteManager = context.spawn(
        RemoteStorageManager(
          rsConnParams,
          context.self
        ),
        "ftp-manager"
      )

      val mqManager = context.spawn(
        MqManager(
          mqConnParams,
          mqQueueName,
          context.self
        ),
        "mq-manager"
      )

      val systemMonitor = context.spawn(
        SystemMonitor(1, context.self),
        "system-monitor"
      )

      val orchestratorSetup = OrchestratorSetup(
        mqManager,
        remoteManager,
        executionManager,
        systemMonitor
      )

      orchestrating(orchestratorSetup, mqExchangeName)
    }

  end setup

  def orchestrating(
      orchestratorSetup: OrchestratorSetup,
      mqExchangeName: MqExchangeName
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ProcessTask(task) =>
            context.log.info(
              s"ProcessTask command received. Task --> $task"
            )
            orchestratorSetup.remoteStorageManager ! RemoteStorageManager
              .DownloadTaskFiles(
                task
              )
            context.self ! RegisterLog(
              task,
              "Task received for processing."
            )

            Behaviors.same

          case GeneralAcknowledgeTask(task) =>
            context.log.info(
              s"GeneralAcknowledgeTask command received. TaskId --> ${task.mqId}."
            )
            orchestratorSetup.messageQueueManager ! MqManager.MqAckTask(
              task.mqId
            )

            context.self ! RegisterLog(
              task,
              "Task ack sent to broker."
            )

            Behaviors.same

          case GeneralRejectTask(task) =>
            context.log.info(
              s"GeneralRejectTask command received. TaskId --> ${task.mqId}."
            )
            orchestratorSetup.messageQueueManager ! MqManager.MqRejectTask(
              task.mqId
            )

            context.self ! RegisterLog(
              task,
              "Task reject sent to broker."
            )

            Behaviors.same

          case RegisterLog(task, log) =>
            context.log.info(
              s"RegisterLog command received. Log --> $log."
            )
            Behaviors.same

          /* **********************************************************************
           * Responses from other actors
           * ********************************************************************** */

          case RemoteStorageManager.TaskDownloaded(task) =>
            context.log.info(
              s"TaskDownloaded response received. TaskId --> ${task.mqId}, Files --> ${task.filePath.toString}."
            )
            orchestratorSetup.executionManager ! ExecutionManager.ExecuteTask(
              task
            )

            context.self ! RegisterLog(
              task,
              "Task files donwloaded for processing."
            )

            Behaviors.same

          case ExecutionManager.TaskExecuted(task) =>
            context.log.info(
              s"TaskExecuted response received. TaskId --> ${task.taskId}"
            )
            orchestratorSetup.remoteStorageManager ! RemoteStorageManager
              .UploadTaskFiles(task)

            context.self ! RegisterLog(task, "Task processing completed.")

            Behaviors.same

          case RemoteStorageManager.TaskUploaded(task) =>
            context.log.info(
              s"TaskUploaded response received. TaskId --> ${task.mqId}, Files --> ${task.filePath.toString}."
            )

            context.self ! RegisterLog(task, "Task files uploaded.")

            if task.routingKeys.tail.nonEmpty then
              val taskForNextStage = task.copy(
                routingKeys = task.routingKeys.tail
              )

              orchestratorSetup.messageQueueManager ! MqManager.MqSendMessage(
                taskForNextStage,
                mqExchangeName,
                RoutingKey(taskForNextStage.routingKeys.head)
              )

              context.self ! RegisterLog(
                taskForNextStage,
                "Task sent for next processing stage."
              )
            end if

            context.self ! GeneralAcknowledgeTask(task)
            Behaviors.same

          case RemoteStorageManager.TaskDownloadFailed(task) =>
            context.log.info(
              s"TaskDownloadFailed response received. TaskId --> ${task.taskId}."
            )

            context.self ! RegisterLog(
              task,
              s"Task files download failed with message: ${task.logMessage}"
            )

            context.self ! GeneralRejectTask(task)
            Behaviors.same

          case RemoteStorageManager.TaskUploadFailed(task) =>
            context.log.info(
              s"TaskUploadFailed response received. TaskId --> ${task.taskId}, Files --> ${task.filePath.toString}."
            )

            context.self ! RegisterLog(
              task,
              s"Task files upload failed with message: ${task.logMessage}"
            )

            context.self ! GeneralRejectTask(task)
            Behaviors.same

          case ExecutionManager.TaskExecutionError(task) =>
            context.log.info(
              s"TaskExecutionError response received. TaskId --> ${task.taskId}."
            )

            context.self ! RegisterLog(
              task,
              s"Task execution failed with message: ${task.logMessage}"
            )

            context.self ! GeneralRejectTask(task)
            Behaviors.same

          case GracefulShutdown(reason) =>
            context.log.info(
              s"GracefulShutdown command received. Reason --> $reason"
            )
            orchestratorSetup.messageQueueManager ! MqManager.GracefulShutdown
            Behaviors.stopped

          case Fail(reason) =>
            context.log.error(
              s"Fail command received. Reason --> $reason"
            )
            Behaviors.stopped
      }
  end orchestrating

end Orchestrator
