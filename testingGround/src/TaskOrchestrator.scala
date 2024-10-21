import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object TaskOrchestrator:
  sealed trait Command
  case object IncreaseProcessors
  case object DecreaseProcessors
  final case class ProcessTask(str: String) extends Command
  private final case class DownloadTask(str: String) extends Command
  private final case class ExecuteTask(str: String) extends Command
  final case class ReportProcessed(str: String) extends Command

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(): Behavior[Command] = orchestrating(5)

  def orchestrating(
      maxProcessors: Int = 5
  ): Behavior[Command] =
    Behaviors.setup { context =>
      val processingManager =
        context.spawn(
          ProcessingManager(maxProcessors, context.self),
          "processing-manager"
        )

      val mqManager = context.spawn(MqManager(), "mq-manager")

      Behaviors.receiveMessage { message =>
        message match
          case IncreaseProcessors =>
            context.log.info(
              s"Increasing max processors from $maxProcessors to ${maxProcessors + 1}"
            )
            orchestrating(maxProcessors + 1)

          case DecreaseProcessors =>
            if maxProcessors > 1 then
              context.log.info(
                s"Decreasing max processors from $maxProcessors to ${maxProcessors - 1}"
              )
              orchestrating(maxProcessors - 1)
            else
              context.log.warn("Cannot decrease processors below 1")
              Behaviors.same

      }
    }
end TaskOrchestrator
