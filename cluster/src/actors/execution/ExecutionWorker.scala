package actors.execution

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import executors.*
import types.Task
import utilities.FileSystemUtil

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task,
      replyTo: ActorRef[Response]
  ) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskPass(task: Task) extends Response
  final case class TaskHalt(task: Task) extends Response

  def apply(): Behavior[Command] = processing()

  /** This behavior represents the processing state of the actor.
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
          if handleExecuteTask(task) then replyTo ! TaskPass(task)
          else replyTo ! TaskHalt(task)
          end if
          Behaviors.stopped

      end match
    }
  end processing

  private def handleExecuteTask(
      task: Task
  ): Boolean =
    // given blockingDispatcher: ExecutionContext =
    //   context.system.classicSystem.dispatchers
    //     .lookup("akka.actor.default-blocking-io-dispatcher")

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
        executeTask(executor, task)
      case None =>
        throw new IllegalArgumentException("No executor available")
    end match
  end handleExecuteTask

  private def executeTask(executor: Executor, task: Task): Boolean =
    println("------------------------ Entering execution")
    val dir = FileSystemUtil.unzipFile(task.relTaskFilePath)
    val canContinue = executor.execute(dir, task)

    val _ = FileSystemUtil.zipFile(task.relTaskFilePath)

    canContinue

  end executeTask
end ExecutionWorker
