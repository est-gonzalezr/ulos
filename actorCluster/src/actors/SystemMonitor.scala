package actors

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import oshi.SystemInfo

import scala.concurrent.duration.*

object SystemMonitor:
  sealed trait Command
  final case class NotifyActiveProcessors(activeProcessors: Int) extends Command
  case object Monitor extends Command
  case object Shutdown extends Command

  sealed trait Response
  case object IncrementProcessors
  case object DecrementProcessors

  def apply(
      maxProcessors: Int,
      ref: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    setup(maxProcessors, ref)

  def setup(
      maxProcessors: Int,
      ref: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("System monitor started...")
      val _ = context.scheduleOnce(1.second, context.self, Monitor)

      def monitorResources(
          maxProcessors: Int,
          activeProcessors: Int
      ): Behavior[Command] =
        Behaviors.receiveMessage { message =>
          message match
            case NotifyActiveProcessors(activeProcessors) =>
              context.log.info(
                s"NotifyActiveProcessors command received. ActiveProcessors --> $activeProcessors"
              )
              monitorResources(maxProcessors, activeProcessors)

            case Monitor =>
              val systemInfo = SystemInfo()
              val cpuUsage = getCpuUsage(systemInfo)
              val ramUsage = getRamUsage(systemInfo)

              context.log.info(s"CPU Usage: $cpuUsage%")
              context.log.info(s"RAM Usage: $ramUsage%")

              val _ = context.scheduleOnce(1.second, context.self, Monitor)

              if cpuUsage > 80 || ramUsage > 90 then
                context.log.info("Decrementing maxProcessors")
                val newProcessorQuantity = maxProcessors - 1
                ref ! Orchestrator.SetProcessorLimit(newProcessorQuantity)
                monitorResources(newProcessorQuantity, activeProcessors)
              else if cpuUsage < 50 && activeProcessors == maxProcessors then
                context.log.info("Incrementing maxProcessors")
                val newProcessorQuantity = maxProcessors + 1
                ref ! Orchestrator.SetProcessorLimit(newProcessorQuantity)
                monitorResources(newProcessorQuantity, activeProcessors)
              else monitorResources(maxProcessors, activeProcessors)
              end if

            case Shutdown =>
              context.log.info("Shutting down system monitor")
              Behaviors.stopped
        }

      monitorResources(maxProcessors, 0)
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
