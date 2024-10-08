import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem

@main def main(): Unit =
  val system = ActorSystem(
    Greeter(Greeter.State(0)),
    "hello-world"
  )

  system ! Greeter.Greet("Alice")
  system ! Greeter.Greet("Bob")
  system ! Greeter.Goodbye("Alice")
  system ! Greeter.Goodbye("Bob")

  system.terminate()

end main

object TaskScheduler:
  def apply(): Behavior[String] =
    Behaviors.setup { context =>
      context.log.info("TaskScheduler started")

      val systemResourcesMonitor = context.spawn(
        SystemResourcesMonitor(),
        "SystemResourcesMonitor"
      )

      val ftpManager = context.spawn(
        FTPManager(),
        "FTPManager"
      )

      val brokerLiaison = context.spawn(
        BrokerLiaison(),
        "BrokerLiaison"
      )

      Behaviors.receiveMessage { message =>
        context.log.info(s"Received message: $message")
        Behaviors.same
      }
    }
end TaskScheduler

object SystemResourcesMonitor:
  def apply(): Behavior[String] =
    Behaviors.receive { (context, message) =>
      context.log.info(s"SystemResourcesMonitor received message: $message")
      Behaviors.same
    }
end SystemResourcesMonitor

object FTPManager:
  def apply(): Behavior[String] =
    Behaviors.receive { (context, message) =>
      context.log.info(s"FTPManager received message: $message")
      Behaviors.same
    }
end FTPManager

object BrokerLiaison:
  def apply(): Behavior[String] =
    Behaviors.receive { (context, message) =>
      context.log.info(s"BrokerLiaison received message: $message")
      Behaviors.same
    }
end BrokerLiaison

object Greeter:
  sealed trait GreetCommand
  final case class Greet(whom: String) extends GreetCommand
  final case class Goodbye(whom: String) extends GreetCommand

  case class State(attendance: Int)

  def apply(state: State): Behavior[GreetCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case Greet(whom) =>
          context.log.info(
            s"Hello, $whom! Attendance:"
              + s"[${state.attendance + 1}]"
          )
          apply(state.copy(attendance = state.attendance + 1))

        case Goodbye(whom) =>
          context.log.info(
            s"Goodbye, $whom! Attendance:" +
              s"[${state.attendance - 1}]"
          )
          apply(state.copy(attendance = state.attendance - 1))
      end match
    }
  end apply
end Greeter
