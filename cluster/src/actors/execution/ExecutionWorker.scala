package actors.execution

import executors.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.DispatcherSelector
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import os.Path
import os.RelPath
import types.Task

import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try

val excludedPatterns = Seq("__MACOSX".r, ".DS_Store".r)

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task
  ) extends Command

  // Private command protocol
  private case class TaskExecutedResult(
      task: Task,
      bool: Boolean
  ) extends Command
  private case class TaskCrashed(th: Throwable) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskPass(task: Task) extends Response
  final case class TaskHalt(task: Task) extends Response

  def apply(replyTo: ActorRef[Response]): Behavior[Command] = processing(
    replyTo
  )

  /** Processing state of the actor.
    *
    * @return
    *   A Behavior that processes a task and then stops.
    */
  private def processing(replyTo: ActorRef[Response]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task) =>
          context.pipeToSelf(handleExecuteTask(context, task)) {
            case Success(bool) => TaskExecutedResult(task, bool)
            case Failure(th)   => TaskCrashed(th)
          }
          Behaviors.same

        /* **********************************************************************
         * Private commands
         * ********************************************************************** */

        case TaskExecutedResult(task, bool) =>
          if bool then replyTo ! TaskPass(task)
          else replyTo ! TaskHalt(task)
          end if
          Behaviors.stopped

        case TaskCrashed(th) =>
          throw th

      end match
    }
  end processing

  private def handleExecuteTask(
      context: ActorContext[Command],
      task: Task
  ): Future[Boolean] =
    given blockingDispatcher: ExecutionContext =
      context.system.dispatchers.lookup(
        DispatcherSelector.fromConfig("blocking-dispatcher")
      )

    val executionPromise = Promise[Boolean]()

    val _ = Future {
      Thread.sleep(task.timeout.duration.toMillis)
      executionPromise.tryFailure(TimeoutException("Task execution timed out"))
    }(using blockingDispatcher)

    val _ = Future {
      Try(executeTask(task)) match
        case Success(result) => executionPromise.trySuccess(result)
        case Failure(th)     => executionPromise.tryFailure(th)
    }(using blockingDispatcher)

    executionPromise.future

    // val executeOrTimeout = Future.firstCompletedOf(
    //   List(
    //     Future({
    //       Thread.sleep(task.timeout.duration.toMillis);
    //       throw TimeoutException("Task execution timed out")
    //     })(using blockingDispatcher),
    //     Future(executeTask(task))(using blockingDispatcher)
    //   )
    // )(using blockingDispatcher)

    // Try(executeOrTimeout) match
    //   case Success(f)  => f
    //   case Failure(th) => Future.failed(th)
    // end match

  end handleExecuteTask

  private def executeTask(task: Task): Boolean =
    val absFilesDir = unzipFile(task.relTaskFilePath)
    val routingKey = task.routingTree.map(node => node.routingKey.value)

    val executorOption = routingKey match
      case Some("skip")  => Some(MockSkipExecutor(task, absFilesDir))
      case Some("pass")  => Some(MockSuccessExecutor(task, absFilesDir))
      case Some("fail")  => Some(MockFailureExecutor(task, absFilesDir))
      case Some("crash") => Some(MockCrashExecutor(task, absFilesDir))
      case Some("cypress-grammar") =>
        Some(CypressGrammarExecutor(task, absFilesDir))
      case Some("cypress-execution") =>
        Some(CypressExecutor(task, absFilesDir))
      case Some("gcode-execution")  => Some(GCodeExecutor(task, absFilesDir))
      case Some("kotlin-execution") => Some(KotlinExecutor(task, absFilesDir))
      case Some(pattern) if pattern.matches("testing.*") =>
        Some(MockSuccessExecutor(task, absFilesDir))
      case _ => None

    executorOption match
      case Some(executor) =>
        val canContinue = executor.execute()
        val _ = zipFile(task.relTaskFilePath)
        canContinue
      case None =>
        throw new IllegalArgumentException(
          s"No executor available for routing key: $routingKey"
        )
    end match
  end executeTask

  private def unzipFile(relPath: RelPath): Path =
    val absPath = os.pwd / relPath
    val unzipPath = absPath / os.up / absPath.baseName
    val tempDir = "temporaryTransferDir"

    val _ = os.remove.all(unzipPath)
    val _ = os.unzip(
      absPath,
      unzipPath,
      excludePatterns = excludedPatterns
    )

    if os.list(unzipPath).length == 1 && os.isDir(os.list(unzipPath).head)
    then
      os.list(unzipPath)
        .foreach(path =>
          os.move(path, path / os.up / tempDir, replaceExisting = true)
        )

      os.list(unzipPath / tempDir)
        .foreach(path =>
          os.move(path, unzipPath / path.last, replaceExisting = true)
        )
      os.remove.all(unzipPath / tempDir)
    end if

    unzipPath
  end unzipFile

  private def zipFile(relPath: RelPath): Path =
    val absPath = os.pwd / relPath
    val zipPath = absPath / os.up / absPath.baseName
    val _ = os.remove.all(absPath)

    os.zip(absPath, Seq(zipPath), excludePatterns = excludedPatterns)

  end zipFile
end ExecutionWorker
