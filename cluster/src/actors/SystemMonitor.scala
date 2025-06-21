package actors

import scala.concurrent.duration.*

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import oshi.SystemInfo

object SystemMonitor:
  sealed trait Command
  case object Monitor extends Command

  def apply(
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    setup(replyTo)

  def setup(
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("System monitor started..")

      val _ = context.scheduleOnce(1.second, context.self, Monitor)
      println(replyTo)
      monitorResources(replyTo)
    }
  end setup

  def monitorResources(
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case Monitor =>
          val _ = replyTo
          val systemInfo = SystemInfo()
          val cpuUsage = getCpuUsage(systemInfo)
          val ramUsage = getRamUsage(systemInfo)

          val _ = context.scheduleOnce(10.second, context.self, Monitor)
          context.log.info(s"CPU Usage: $cpuUsage%, RAM Usage: $ramUsage%")
          Behaviors.same
    }

  def getCpuUsage(systemInfo: SystemInfo): Double =
    val processors = systemInfo.getHardware().getProcessor()
    processors.getSystemCpuLoad(1000) * 100
  end getCpuUsage

  def getRamUsage(systemInfo: SystemInfo): Double =
    val memory = systemInfo.getHardware().getMemory()
    val totalMemory = memory.getTotal().toDouble
    val availableMemory = memory.getAvailable().toDouble
    val usedMemoryPercentage =
      (totalMemory - availableMemory) / totalMemory * 100
    usedMemoryPercentage
  end getRamUsage
end SystemMonitor
