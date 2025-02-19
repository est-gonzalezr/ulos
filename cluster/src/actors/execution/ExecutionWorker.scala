package actors.execution

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.Task
import utilities.DockerUtil
import utilities.FileSystemUtil

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import java.nio.file.FileSystem

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task,
      replyTo: ActorRef[StatusReply[Task]]
  ) extends Command

  def apply(): Behavior[Command] = processing()

  /** This behavior represents the processing state of the actor.
    *
    * @return
    *   A behavior that processes a task and then stops.
    */
  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task, replyTo) =>
          // context.log.info(
          //   s"ExecuteTask command received. Task --> $task."
          // )

          executionResult(task) match
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
          end match
      end match

      Behaviors.stopped
    }
  end processing

  private def executionResult(task: Task): Try[Task] =
    Try {
      println("------------------------ Entering execution")
      FileSystemUtil.unzipFile(task.relTaskFilePath) match
        case Success(dir) =>
          val (containerId, exitCode, output) = DockerUtil.runContainer(
            dir,
            "cypress/included",
            "run -b electron"
          )

          println("------------------------------------------")
          println(containerId)
          println(exitCode)
          println(output)
          println("------------------------------------------")

          os.write.over(
            dir / s"output_${task.taskDefinition.stages.head(0)}.txt",
            output
          )

          val _ = FileSystemUtil.zipFile(task.relTaskFilePath)

          // println("Sleeping for 5 sec")
          // Thread.sleep(20000)
          // println("Waking up")

          task

        case Failure(exception) =>
          println(exception)
          throw Throwable("Could not unzip file")
      end match
    }

    // println("Sleeping for 5 sec")
    // Thread.sleep(5000)
    // println("Waking up")
    // Try(task)
  end executionResult
end ExecutionWorker
