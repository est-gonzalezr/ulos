package actors.execution

/** @author
  *   Esteban Gonzalez Ruales
  */

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import executors.CypressExecutor
import executors.CypressGrammarExecutor
import executors.Executor
import executors.GCodeExecutor
import executors.KotlinExecutor
import executors.MockExecutor
import types.Task
import utilities.FileSystemUtil

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = processing()

  /** This behavior represents the processing state of the actor.
    *
    * @return
    *   A behavior that processes a task and then stops.
    */
  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      implicit val blockingDispatcher =
        context.system.classicSystem.dispatchers
          .lookup("blooking-io-dispatcher")

      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task, replyTo) =>
          // context.log.info(
          //   s"ExecuteTask command received. Task --> $task."
          // )

          val executorOption = task.routingKeys.headOption match
            case Some("cypress-grammar-checking") =>
              Some(CypressGrammarExecutor)
            case Some("cypress-execution") => Some(CypressExecutor)
            case Some("gcode-execution")   => Some(GCodeExecutor)
            case Some("kotlin-execution")  => Some(KotlinExecutor)
            case Some(pattern) if "testing*".r.matches(pattern) =>
              Some(MockExecutor)
            case _ => None

          executorOption match
            case Some(executor) =>
              Future {
                executeTask(executor, task)
              }(using blockingDispatcher).onComplete {
                case Success(tryTask) =>
                  // replyTo ! StatusReply.Success(tryTask)
                  tryTask match
                    case Success(_) =>
                      replyTo ! StatusReply.Ack
                    case Failure(exception) =>
                      replyTo ! StatusReply.Error(exception)

                case Failure(exception) =>
                  replyTo ! StatusReply.Error(exception)
              }
            case None =>
              replyTo ! StatusReply.Error("No executor available")
          end match
      end match

      Behaviors.stopped
    }
  end processing

  private def executeTask(executor: Executor, task: Task): Try[Task] =
    println("------------------------ Entering execution")
    FileSystemUtil.unzipFile(task.relTaskFilePath) match
      case Success(dir) =>
        val executedTask = executor.execute(dir, task)

        val _ = FileSystemUtil.zipFile(task.relTaskFilePath)

        executedTask

      case Failure(exception) =>
        println(exception)
        Failure(Throwable("Could not unzip file"))
    end match

  end executeTask
end ExecutionWorker
