import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object MqParser:
  sealed trait Command
  final case class Parse(message: Seq[Byte], replyTo: ActorRef[Response])
      extends Command

  sealed trait Response
  final case class Parsed(taskType: String, taskPath: String) extends Response

  def apply(): Behavior[Command] = parsing

  def parsing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case Parse(message, replyTo) =>
          context.log.info("Parsing MQ message")
          replyTo ! Parsed("Cypress", "path")
          Behaviors.stopped
    }
  end parsing
end MqParser
