import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object SystemMonitor:
  sealed trait Command
  case object GetResourceStatus extends Command

  sealed trait Response
  case object IncrementProcessors
  case object DecrementProcessors

  def apply(ref: ActorRef[Orchestrator.Command]): Behavior[Command] =
    monitorResources(ref)

  def monitorResources(
      ref: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case GetResourceStatus =>
          // this is a dummy implementation
          // in a real world scenario, we would query the system
          // for the current resource status
          context.log.info("Querying system for resource status")
          Behaviors.same
    }
end SystemMonitor
