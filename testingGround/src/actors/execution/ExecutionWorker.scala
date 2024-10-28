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
import akka.pattern.StatusReply

object ExecutionWorker:
  sealed trait Command
  final case class ExecuteTask(
      task: Task,
      replyTo: ActorRef[StatusReply[TaskExecuted]]
  ) extends Command

  sealed trait Response
  final case class TaskExecuted(task: Task) extends Response

  def apply(): Behavior[Command] = processing()

  /** This behavior represents the processing state of the actor.
    *
    * @return
    *   A behavior that processes a task and then stops.
    */
  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case ExecuteTask(task, replyTo) =>
          context.log.info(
            s"Received task of type: ${task.taskType}"
          )

          val processedTask = process(task)

          replyTo ! StatusReply.Success(TaskExecuted(processedTask))

          Behaviors.stopped
    }
  end processing

  private def process(task: Task): Task =
    val endTime = System.currentTimeMillis() + 5000
    while System.currentTimeMillis() < endTime do ()
    end while
    task
  end process
end ExecutionWorker
