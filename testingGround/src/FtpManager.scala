import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success


object FtpManager:
  sealed trait Command
  final case class UploadFile(
      str: String
  ) extends Command

  final case class

  sealed trait Response
  case object SuccessfulUpload extends Response
  case object UnsuccessfulUpload extends Response
end FtpManager
