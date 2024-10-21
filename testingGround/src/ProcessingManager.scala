import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

// here it is necesaary to have a max and active processor count since
// if we need to decrease the number of processors and all are active,
// then the command will fail since decreasing the number of processors would be 0

object ProcessingManager:
  sealed trait Command
  final case class Process(str: String) extends Command
  final case class ReportProcessed(response: String) extends Command
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command

  sealed trait Response
  final case class TaskProcessed(str: String) extends Response
  final case class TaskFailed(str: String) extends Response

  implicit val timeout: Timeout = 10.seconds

  def apply(
      maxProcessors: Int,
      ref: ActorRef[TaskOrchestrator.Command]
  ): Behavior[Command] =
    delegateProcessing(0, maxProcessors, ref)
  end apply

  def delegateProcessing(
      activeProcessors: Int,
      maxProcessors: Int,
      ref: ActorRef[TaskOrchestrator.Command]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case IncreaseProcessors =>
          context.log.info(
            s"Increasing max processors from $maxProcessors to ${maxProcessors + 1}"
          )
          delegateProcessing(activeProcessors, maxProcessors + 1, ref)

        case DecreaseProcessors =>
          if maxProcessors > 1 then
            context.log.info(
              s"Decreasig max processors from $maxProcessors to ${maxProcessors - 1}"
            )
            delegateProcessing(activeProcessors, maxProcessors - 1, ref)
          else
            context.log.warn("Cannot decrease processors below 1")
            Behaviors.same

        case Process(str) =>
          if activeProcessors < maxProcessors then
            context.log.info(s"Delegating task to processor: $str")

            val processor =
              context.spawn(Processor(), s"processor-$activeProcessors")

            context.ask(
              processor,
              ref => Processor.Process(str, ref)
            ) {
              case Success(_) =>
                ReportProcessed("Task processed")
              case Failure(_) =>
                ReportProcessed("Task processing failed")
            }

            delegateProcessing(activeProcessors + 1, maxProcessors, ref)
          else
            context.log.info("All processors are busy")
            Behaviors.same

        case ReportProcessed(response) =>
          context.log.info(response)
          ref ! TaskOrchestrator.ReportProcessed(response)
          delegateProcessing(activeProcessors - 1, maxProcessors, ref)
    }

  end delegateProcessing
end ProcessingManager
