import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object MqAcker:
  sealed trait Command
  final case class SendAck(
      taskType: String,
      taskPath: String,
      replyTo: ActorRef[Response]
  ) extends Command

  final case class SendNack(
      taskType: String,
      taskPath: String,
      replyTo: ActorRef[Response]
  ) extends Command

  sealed trait Response
  case object SuccessfulAck extends Response
  case object UnsuccessfulAck extends Response
end MqAcker
