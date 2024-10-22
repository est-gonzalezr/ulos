import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}
import sbt.testing.Task

object MqManager:
  // Actor Protocol
  sealed trait Command
  final case class ProcessMqMessage(message: Seq[Byte]) extends Command
  final case class ProcessTask(taskType: String, taskPath: String)
      extends Command
  final case class ReportException(exception: Throwable) extends Command

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(ref: ActorRef[Orchestrator.Command]): Behavior[Command] =
    processing(ref)

  def processing(ref: ActorRef[Orchestrator.Command]): Behavior[Command] =
    Behaviors.setup { context =>
      val mqConsumer = context.spawn(MqConsumer(context.self), "mq-consumer")

      Behaviors.receiveMessage { message =>
        message match
          case ProcessMqMessage(bytes) =>
            val mqParser = context.spawn(MqMessageParser(), "mq-parser")

            context.ask(
              mqParser,
              ref => MqMessageParser.DeserializeMessage(bytes, ref)
            ) {
              case Success(_) =>
                ProcessTask("cypress", "path")
              case Failure(ex) =>
                ReportException(ex)
            }
            Behaviors.same
          case ProcessTask(taskType, taskPath) =>
            ref ! Orchestrator.ProcessTask("cypress")
            Behaviors.same

          case ReportException(exception) =>
            // ref ! s"Exception: $exception"
            Behaviors.same
      }
    }
  end processing
end MqManager
