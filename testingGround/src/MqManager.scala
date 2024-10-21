import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object MqManager:
  sealed trait Command
  final case class ProcessMqMessage(message: Seq[Byte]) extends Command
  final case class ProcessTask(taskType: String, taskPath: String)
      extends Command
  final case class ReportException(exception: Throwable) extends Command

  implicit val timeout: Timeout = 10.seconds

  def apply(): Behavior[Command] = processing()

  def processing(): Behavior[Command] =
    Behaviors.setup { context =>
      val mqParser = context.spawn(MqParser(), "mq-parser")

      Behaviors.receiveMessage { message =>
        message match
          case ProcessMqMessage(bytes) =>
            context.ask(
              mqParser,
              ref => MqParser.Parse(bytes, ref)
            ) {
              case Success(_) =>
                ProcessTask("cypress", "path")
              case Failure(ex) =>
                ReportException(ex)
            }
            Behaviors.same
          case ProcessTask(taskType, taskPath) =>
            // ref ! s"Process task of type: $taskType"
            Behaviors.same

          case ReportException(exception) =>
            // ref ! s"Exception: $exception"
            Behaviors.same
      }
    }
  end processing
end MqManager
