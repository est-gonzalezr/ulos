package actors.execution

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import executors.*
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
      // implicit val blockingDispatcher =
      //   context.system.classicSystem.dispatchers
      //     .lookup("blooking-io-dispatcher")

      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task, replyTo) =>
          handleExecuteTask(context, task, replyTo)
          replyTo ! StatusReply.Ack
      end match

      Behaviors.stopped
    }
  end processing

  private def handleExecuteTask(
      context: ActorContext[Command],
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ): Unit =
    given blockingDispatcher: ExecutionContext =
      context.system.classicSystem.dispatchers
        .lookup("akka.actor.default-blocking-io-dispatcher")

    val executorOption = task.routingKeys.headOption match
      case Some("cypress-grammar-checking") => Some(CypressGrammarExecutor)
      case Some("cypress-execution")        => Some(CypressExecutor)
      case Some("gcode-execution")          => Some(GCodeExecutor)
      case Some("kotlin-execution")         => Some(KotlinExecutor)
      case Some(pattern) if "testing*".r.matches(pattern) =>
        Some(MockExecutor)
      case _ => None

    executorOption match
      case Some(executor) =>
        Future {
          executeTask(executor, task)
        }(using blockingDispatcher).onComplete {
          case Success(tryTask) =>
            tryTask match
              case Success(_) =>
                replyTo ! StatusReply.Ack
              case Failure(th) =>
                replyTo ! StatusReply.Error(th)

          case Failure(th) =>
            replyTo ! StatusReply.Error(th)
        }
      case None =>
        replyTo ! StatusReply.Error("No executor available")
    end match
  end handleExecuteTask

  private def executeTask(executor: Executor, task: Task): Try[Task] =
    println("------------------------ Entering execution")
    FileSystemUtil.unzipFile(task.relTaskFilePath) match
      case Success(dir) =>
        val executedTask = executor.execute(dir, task)

        val _ = FileSystemUtil.zipFile(task.relTaskFilePath)

        executedTask

      case Failure(th) =>
        println(th)
        Failure(Throwable("Could not unzip file"))
    end match
  end executeTask
end ExecutionWorker
