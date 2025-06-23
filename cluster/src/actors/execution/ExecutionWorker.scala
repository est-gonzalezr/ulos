package actors.execution

import executors.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import types.Task
import utilities.FileSystemUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task,
      replyTo: ActorRef[Response]
  ) extends Command

  // Private command protocol
  private case class TaskExecutedResult(
      task: Task,
      bool: Boolean,
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
    *   A Behavior that processes a task and then stops.
    */
  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task, replyTo) =>
          context.pipeToSelf(handleExecuteTask(context, task)) {
            case Success(bool) => TaskExecutedResult(task, bool, replyTo)
            case Failure(th)   => throw th
          }
          Behaviors.same

        /* **********************************************************************
         * Private commands
         * ********************************************************************** */

        case TaskExecutedResult(task, bool, replyTo) =>
          if bool then replyTo ! TaskPass(task)
          else replyTo ! TaskHalt(task)
          end if
          Behaviors.stopped

      end match
    }
  end processing

  private def handleExecuteTask(
      context: ActorContext[Command],
      task: Task
  ): Future[Boolean] =
    given blockingDispatcher: ExecutionContext =
      context.system.classicSystem.dispatchers
        .lookup("akka.actor.default-blocking-io-dispatcher")

    val executorOption =
      task.routingKeys.headOption.map(elem => elem(0).value) match
        case Some("pass")              => Some(MockSuccessExecutor)
        case Some("fail")              => Some(MockFailureExecutor)
        case Some("crash")             => Some(MockCrashExecutor)
        case Some("cypress-grammar")   => Some(CypressGrammarExecutor)
        case Some("cypress-execution") => Some(CypressExecutor)
        case Some("gcode-execution")   => Some(GCodeExecutor)
        case Some("kotlin-execution")  => Some(KotlinExecutor)
        case Some(pattern) if "testing*".r.matches(pattern) =>
          Some(MockSuccessExecutor)
        case _ => None

    executorOption match
      case Some(executor) =>
        Future(executeTask(executor, task))(using blockingDispatcher)
      case None =>
        throw new IllegalArgumentException(
          s"No executor available for routing key: ${task.routingKeys.headOption}"
        )
    end match
  end handleExecuteTask

  private def executeTask(executor: Executor, task: Task): Boolean =
    val dir = FileSystemUtil.unzipFile(task.relTaskFilePath)
    val canContinue = executor.execute(dir, task)
    val _ = FileSystemUtil.zipFile(task.relTaskFilePath)

    canContinue

  end executeTask
end ExecutionWorker
