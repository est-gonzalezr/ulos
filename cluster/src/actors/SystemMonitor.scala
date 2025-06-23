package actors

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import oshi.SystemInfo

import scala.concurrent.duration.*

object SystemMonitor:
  sealed trait Command
  case object Monitor extends Command

  def apply(
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    setup(replyTo)

  private def setup(
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("System monitor started..")

      val _ = context.scheduleOnce(1.second, context.self, Monitor)
      monitorResources(replyTo)
    }
  end setup

  private def monitorResources(
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case Monitor =>
          val _ = replyTo
          val systemInfo = SystemInfo()
          val cpuUsage = round(getCpuUsage(systemInfo), 2)
          val ramUsage = round(getRamUsage(systemInfo), 2)

          val _ = context.scheduleOnce(10.second, context.self, Monitor)
          context.log.info(s"CPU Usage: $cpuUsage%, RAM Usage: $ramUsage%")
          Behaviors.same
    }

  private def getCpuUsage(systemInfo: SystemInfo): Double =
    val processors = systemInfo.getHardware().getProcessor()
    processors.getSystemCpuLoad(1000) * 100
  end getCpuUsage

  private def getRamUsage(systemInfo: SystemInfo): Double =
    val memory = systemInfo.getHardware().getMemory()
    val totalMemory = memory.getTotal().toDouble
    val availableMemory = memory.getAvailable().toDouble
    val usedMemoryPercentage =
      (totalMemory - availableMemory) / totalMemory * 100
    usedMemoryPercentage
  end getRamUsage

  private def round(value: Double, precision: Int): Double =
    require(precision >= 0, "Precision must be non-negative")
    (value * math.pow(10, precision)).round / math.pow(10, precision)
  end round

end SystemMonitor
