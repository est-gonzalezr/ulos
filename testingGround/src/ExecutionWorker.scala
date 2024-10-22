/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object ExecutionWorker:
  sealed trait Command
  final case class ExecuteTask(str: String, replyTo: ActorRef[Response])
      extends Command

  sealed trait Response
  final case class TaskExecuted(str: String) extends Response

  def apply(): Behavior[Command] = processing

  /** This behavior represents the processing state of the actor.
    *
    * @return
    *   A behavior that processes a task and then stops.
    */
  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case ExecuteTask(str, replyTo) =>
          context.log.info(
            s"Received task of type: $str"
          )

          process(str)

          context.log.info(
            s"Task processed: $str"
          )
          replyTo ! TaskExecuted(str)
          Behaviors.stopped
    }
  end processing

  private def process(taskType: String): Unit =
    val endTime = System.currentTimeMillis() + 5000
    while System.currentTimeMillis() < endTime do ()
    end while
  end process
end ExecutionWorker
