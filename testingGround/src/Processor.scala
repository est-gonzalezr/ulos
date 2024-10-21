import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object Processor:
  sealed trait Command
  final case class Process(
      taskType: String,
      taskPath: String,
      replyTo: ActorRef[Response]
  ) extends Command

  sealed trait Response
  final case class Processed(taskType: String, taskPath: String)
      extends Response

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case Process(taskType, taskPath, replyTo) =>
          context.log.info(
            s"Processor received task of type: $taskType"
          )
          process(taskType)
          replyTo ! Processed(taskType, taskPath)
          Behaviors.same
    }
  end processing

  private def process(taskType: String): Unit =
    println(s"Processing task of type: $taskType...")
    val endTime = System.currentTimeMillis() + 5000
    while System.currentTimeMillis() < endTime do ()
    end while
    println(s"Task of type: $taskType processed")
  end process

end Processor
