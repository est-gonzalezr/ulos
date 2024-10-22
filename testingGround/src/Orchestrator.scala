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

  // private type CommandOrResponse = Command | ExecutionManager.Response

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(): Behavior[Command] = orchestrating

  def orchestrating: Behavior[Command] =
    Behaviors.setup { context =>
      val processingManager =
        context.spawn(
          ExecutionManager(5, context.self),
          "processing-manager"
        )

      val ftpManager = context.spawn(FtpManager(5, context.self), "ftp-manager")

      val mqManager = context.spawn(MqManager(context.self), "mq-manager")

      Behaviors.receiveMessage { message =>
        message match
          case IncreaseProcessors =>
            processingManager ! ExecutionManager.IncreaseProcessors
            Behaviors.same

          case DecreaseProcessors =>
            processingManager ! ExecutionManager.DecreaseProcessors
            Behaviors.same

          case ExecutionManager.TaskExecuted(str) =>
            context.log.info(s"Task processed: $str")
            Behaviors.same

      }
    }.narrow
end Orchestrator
