package actors

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

import types.MqMessage
import types.Task

object Orchestrator:
  sealed trait Command
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class ProcessTask(task: Task) extends Command
  private final case class DownloadTaskFiles(task: Task) extends Command
  private final case class UploadTaskFiles(task: Task) extends Command
  private final case class ExecuteTask(task: Task) extends Command
  final case class ReportProcessed(task: Task) extends Command

//   private val defaultProcessors = 5
//   private type CommandOrResponse = Command | ExecutionManager.Response |
//     FtpManager.Response

//   implicit val timeout: Timeout = Timeout(10.seconds)

//   def apply(): Behavior[Command] = orchestrating()

//   def orchestrating(): Behavior[Command] =
//     Behaviors
//       .setup[CommandOrResponse] { context =>
//         context.log.info("Orchestrator started...")

//         val executionManager =
//           context.spawn(
//             ExecutionManager(defaultProcessors, context.self),
//             "processing-manager"
//           )

//         val ftpManager =
//           context.spawn(
//             FtpManager(defaultProcessors, context.self),
//             "ftp-manager"
//           )

//         val mqManager = context.spawn(MqManager(context.self), "mq-manager")

//         Behaviors
//           .receiveMessage[CommandOrResponse] { message =>
//             message match
//               // Messages from system monitor
//               case IncreaseProcessors =>
//                 executionManager ! ExecutionManager.IncreaseProcessors
//                 Behaviors.same

//               case DecreaseProcessors =>
//                 executionManager ! ExecutionManager.DecreaseProcessors
//                 Behaviors.same

//               // Messages from MQ Manager
//               case ProcessTask(task) =>
//                 context.log.info(s"Processing task: ${task.taskType} from MQ")
//                 context.self ! DownloadTaskFiles(task)
//                 Behaviors.same

//               // Messages from self
//               case DownloadTaskFiles(task) =>
//                 context.log.info(s"Downloading file: ${task.taskType}")
//                 ftpManager ! FtpManager.DownloadTask(task)
//                 Behaviors.same

//               case UploadTaskFiles(task) =>
//                 context.log.info(s"Uploading file: ${task.taskType}")
//                 ftpManager ! FtpManager.UploadTask(task)
//                 Behaviors.same

//               case ExecuteTask(task) =>
//                 context.log.info(s"Executing task: ${task.taskType}")
//                 executionManager ! ExecutionManager.ExecuteTask(task)
//                 Behaviors.same

//               // Messages from Execution Manager

//               case ExecutionManager.TaskExecuted(task) =>
//                 context.log.info(s"Task processed: ${task.taskType}")
//                 mqManager ! MqManager.SerializeMqMessage(task)
//                 Behaviors.same

//               // Messages from Ftp Manager

//               case FtpManager.SuccessfulUpload =>
//                 context.log.info("File uploaded successfully")
//                 Behaviors.same

//               case FtpManager.UnsuccessfulUpload =>
//                 context.log.error("File upload failed")
//                 Behaviors.same
//           }
//       }
//       .narrow
end Orchestrator
