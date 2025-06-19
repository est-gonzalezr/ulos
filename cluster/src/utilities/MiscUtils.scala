package utilities

import akka.actor.typed.scaladsl.ActorContext

object MiscUtils:
  def defineRetryCommand[T](
      context: ActorContext[T],
      retries: Int,
      failureMessage: String,
      retryCommand: => T,
      retriesExhaustedCommand: => T
  ): T =
    if retries > 0 then
      context.log.warn(s"$failureMessage. Retrying... ($retries left)")
      retryCommand
    else
      context.log.error(s"$failureMessage. Retries exhausted.")
      context.log.error(
        s"******************** CONTACT SYSTEM ADMINISTRATOR!!! ********************"
      )
      retriesExhaustedCommand
end MiscUtils
