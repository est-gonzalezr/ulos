package actors

import scala.concurrent.duration.*

import actors.execution.ExecutionManager
import actors.mq.MqManager
import actors.storage.RemoteStorageManager
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.ChildFailed
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import types.MessageQueueConnectionParams
import types.OpaqueTypes.MqExchangeName
import types.OpaqueTypes.MqQueueName
import types.OpaqueTypes.RoutingKey
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

  private type CommandOrResponse =
    Command | ExecutionManager.Response | RemoteStorageManager.Response |
      MqManager.Response

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

      val supervisedExecutionManager = Behaviors
        .supervise(ExecutionManager(context.self))
        .onFailure(
          SupervisorStrategy.restart
            .withLimit(MaxConsecutiveRestarts, 10.minutes)
        )
      val executionManager = context.spawn(
        supervisedExecutionManager,
        "execution-manager"
      )
      context.watch(executionManager)

      val supervisedRsManager = Behaviors
        .supervise(RemoteStorageManager(rsConnParams, context.self))
        .onFailure(
          SupervisorStrategy
            .restartWithBackoff(1.seconds, 5.seconds, 0.2)
            .withMaxRestarts(MaxConsecutiveRestarts)
        )
      val rsManager = context.spawn(
        supervisedRsManager,
        "ftp-manager"
      )
      context.watch(rsManager)

      val supervisedMqManager = Behaviors
        .supervise(MqManager(mqConnParams, mqQueueName, context.self))
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

      val systemMonitor = context.spawn(
        SystemMonitor(1, context.self),
        "system-monitor"
      )

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
            context.log.info(
              s"Have to implement!!! RegisterLog command received. Log --> $log."
            )
            Behaviors.same

          /* **********************************************************************
           * Responses
           * ********************************************************************** */

          case RemoteStorageManager.TaskDownloaded(task) =>
            context.log.info(
              s"TaskDownloaded response received. TaskId --> ${task.mqId}, Files --> ${task.filePath.toString}."
            )
            context.self ! RegisterLog(
              task,
              "Task files donwloaded for processing."
            )

            setup.executionManager ! ExecutionManager.ExecuteTask(
              task
            )

            Behaviors.same

          case ExecutionManager.TaskPass(task) =>
            context.log.info(
              s"TaskExecuted response received. TaskId --> ${task.taskId}"
            )
            context.self ! RegisterLog(task, "Task processing completed.")

            setup.remoteStorageManager ! RemoteStorageManager
              .UploadTaskFiles(task)

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

              setup.messageQueueManager ! MqManager.PublishTask(
                taskForNextStage,
                mqExchangeName,
                RoutingKey(taskForNextStage.routingKeys.head)
              )

              context.self ! RegisterLog(
                taskForNextStage,
                "Task sent for next processing stage."
              )
            else
              setup.messageQueueManager ! MqManager.AckTask(
                task.mqId
              )
            end if

            Behaviors.same

          case RemoteStorageManager.TaskDownloadFailed(task, reason) =>
            context.log.info(
              s"TaskDownloadFailed response received. TaskId --> ${task.taskId}."
            )

            context.self ! RegisterLog(
              task.copy(logMessage = Some(reason.getMessage())),
              s"Task files download failed with reason: ${reason}"
            )

            setup.messageQueueManager ! MqManager.RejectTask(task)
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
            // setup.messageQueueManager ! MqManager.GracefulShutdown
            Behaviors.stopped

          case Fail(reason) =>
            context.log.error(
              s"Fail command received. Reason --> $reason"
            )
            Behaviors.stopped
        end match
      }
      .receiveSignal { case (context, ChildFailed(ref)) =>
        context.log.error(s"Child actor failed: $ref")
        Behaviors.same
      }
  end orchestrating

end Orchestrator
