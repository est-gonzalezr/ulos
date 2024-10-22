import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object Orchestrator:
  sealed trait Command
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class ProcessTask(str: String) extends Command
  private final case class DownloadTask(str: String) extends Command
  private final case class ExecuteTask(str: String) extends Command
  final case class ReportProcessed(str: String) extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    FtpManager.Response

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(): Behavior[Command] = orchestrating

  def orchestrating: Behavior[Command] =
    Behaviors
      .setup[Any] { context =>
        context.log.info("Orchestrator started...")

        val executionManager =
          context.spawn(
            ExecutionManager(5, context.self),
            "processing-manager"
          )

        val ftpManager =
          context.spawn(FtpManager(5, context.self), "ftp-manager")

        val mqManager = context.spawn(MqManager(context.self), "mq-manager")

        Behaviors
          .receiveMessage[Any] { message =>
            message match
              case IncreaseProcessors =>
                executionManager ! ExecutionManager.IncreaseProcessors
                Behaviors.same

              case DecreaseProcessors =>
                executionManager ! ExecutionManager.DecreaseProcessors
                Behaviors.same

              case ProcessTask(str) =>
                context.log.info(s"Processing task: $str from MQ")
                ftpManager ! FtpManager.DownloadFile(str)
                Behaviors.same

              case ExecutionManager.TaskExecuted(str) =>
                context.log.info(s"Task processed: $str")
                Behaviors.same

              case FtpManager.SuccessfulUpload =>
                context.log.info("File uploaded successfully")
                Behaviors.same

              case FtpManager.UnsuccessfulUpload =>
                context.log.error("File upload failed")
                Behaviors.same

              case MqManager.ProcessTask(taskType, taskPath) =>
                context.log.info(s"Received task of type: $taskType")
                Behaviors.same

          }
      }
      .narrow
end Orchestrator
