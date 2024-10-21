import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object ProcessingManager:
  sealed trait Command
  final case class Process(taskType: String, taskPath: String) extends Command
  final case class ReportProcessed(response: String) extends Command

  implicit val timeout: Timeout = 10.seconds

  def apply(activeProcessors: Int, maxProcessors: Int): Behavior[Command] =
    delegateProcessing(activeProcessors, maxProcessors)
  end apply

  def delegateProcessing(
      activeProcessors: Int,
      maxProcessors: Int
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case Process(taskType, taskPath) =>
          context.log.info(
            s"ProcessingManager received task of type: $taskType"
          )
          val processor =
            context.spawn(Processor(), s"processor-$activeProcessors")
          context.ask(
            processor,
            ref => Processor.Process(taskType, taskPath, ref)
          ) {
            case Success(_) =>
              ReportProcessed("Task processed")
            case Failure(_) =>
              ReportProcessed("Task processing failed")
          }

          delegateProcessing(activeProcessors + 1, maxProcessors)

        case ReportProcessed(response) =>
          context.log.info(response)
          delegateProcessing(activeProcessors - 1, maxProcessors)
    }

  end delegateProcessing
end ProcessingManager
