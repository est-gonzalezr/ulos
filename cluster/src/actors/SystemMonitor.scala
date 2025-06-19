package actors

import scala.concurrent.duration.*

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import oshi.SystemInfo

object SystemMonitor:
  sealed trait Command
  final case class NotifyActiveProcessors(activeProcessors: Int) extends Command
  case object Monitor extends Command

  sealed trait Response
  case object IncrementProcessors
  case object DecrementProcessors

  def apply(
      maxProcessors: Int,
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    setup(maxProcessors, replyTo)

  def setup(
      maxProcessors: Int,
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("System monitor started...")

      val _ = context.scheduleOnce(1.second, context.self, Monitor)
      println(replyTo)

      def monitorResources(
          maxProcessors: Int,
          activeProcessors: Int
      ): Behavior[Command] =
        Behaviors.receiveMessage { message =>
          message match
            case NotifyActiveProcessors(activeProcessors) =>
              // context.log.info(
              //   s"NotifyActiveProcessors command received. ActiveProcessors --> $activeProcessors",
              // )
              monitorResources(maxProcessors, activeProcessors)

            case Monitor =>
              // context.log.info("Monitor command received.")

              val systemInfo = SystemInfo()
              val cpuUsage = getCpuUsage(systemInfo)
              val ramUsage = getRamUsage(systemInfo)

              // context.log.info(s"CPU Usage: $cpuUsage%")
              // context.log.info(s"RAM Usage: $ramUsage%")

              val _ = context.scheduleOnce(5.second, context.self, Monitor)

              if cpuUsage > 90 || ramUsage > 90 then
                val newProcessorQuantity = maxProcessors - 1

                // if newProcessorQuantity > 0 then
                //   // context.log.info("Decrementing maxProcessors")
                //   // replyTo ! Orchestrator.SetProcessorLimit(newProcessorQuantity)
                // // else
                // //   // context.log.info(
                // //   //   "No processors available. Recommending shutdown.",
                // //   // )
                // //   replyTo ! Orchestrator.GracefulShutdown(
                // //     "Processing power exhausted.",
                // //   )
                // end if
                monitorResources(newProcessorQuantity, activeProcessors)
              else if cpuUsage < 50 && activeProcessors == maxProcessors then
                // context.log.info("Incrementing maxProcessors")
                val newProcessorQuantity = maxProcessors + 1
                // replyTo ! Orchestrator.SetProcessorLimit(newProcessorQuantity)
                monitorResources(newProcessorQuantity, activeProcessors)
              else monitorResources(maxProcessors, activeProcessors)
              end if
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
