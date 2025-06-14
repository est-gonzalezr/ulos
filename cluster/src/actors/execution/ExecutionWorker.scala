package actors.execution

/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import executors.CypressExecutor
import executors.Executor
import types.Task
import utilities.FileSystemUtil

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
    task: Task,
    replyTo: ActorRef[StatusReply[Task]],
  ) extends Command

  def apply(): Behavior[Command] = processing()

  /**
   * This behavior represents the processing state of the actor.
   *
   * @return
   *   A behavior that processes a task and then stops.
   */
  def processing(): Behavior[Command] =
    Behaviors.receive { (_, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task, replyTo) =>
          // context.log.info(
          //   s"ExecuteTask command received. Task --> $task."
          // )

          val executorOption = task.routingKeys.headOption match
            case Some("processing") => Some(CypressExecutor)
            case _                  => None

          executorOption match
            case Some(executor) =>
              executeTask(executor, task) match
                case Success(executedTask) =>
                  // context.log.info(
                  //   s"Execution success. Task --> $task."
                  // )
                  // context.log.info(
                  //   s"Sending StatusReply.Success to ExecutionManager. Task --> $task."
                  // )

                  replyTo ! StatusReply.Success(executedTask)
                case Failure(exception) =>
                  // context.log.error(
                  //   s"Execution failed. Task --> $task. Exception thrown: ${exception.getMessage()}."
                  // )
                  // context.log.info(
                  //   s"Sending StatusReply.Error to ExecutionManager. Task --> $task."
                  // )

                  replyTo ! StatusReply.Error(exception)
            case None =>
              replyTo ! StatusReply.Error("No executor available")
          end match
      end match

      Behaviors.stopped
    }
  end processing

  private def executeTask(executor: Executor, task: Task): Try[Task] =
    Try {
      println("------------------------ Entering execution")
      FileSystemUtil.unzipFile(task.relTaskFilePath) match
        case Success(dir) =>
          val executedTask = executor.execute(dir, task)

          val _ = FileSystemUtil.zipFile(task.relTaskFilePath)

          executedTask

        case Failure(exception) =>
          println(exception)
          throw Throwable("Could not unzip file")
      end match
    }
  end executeTask
end ExecutionWorker
