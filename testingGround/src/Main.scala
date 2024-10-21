import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.*
import akka.util.Timeout
import scala.concurrent.duration.*
import scala.util.{Success, Failure}

@main def main(): Unit =
  println("Hello, world!")

  val guardian = ActorSystem(ProcessingManager(0, 5), "processing-manager")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian ! ProcessingManager.Process("Cypress", "path")
  guardian.terminate()
end main
