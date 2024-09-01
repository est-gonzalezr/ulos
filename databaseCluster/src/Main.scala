/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import startup.ConsumerProgram

val DefaultProcessingConsumerQuantity = 1

object DatabaseCluster extends ConsumerProgram, IOApp:

  /** The entry point of the program.
    *
    * @param args
    *   The arguments passed to the program
    * @return
    *   An IO monad that represents the entry point of the program
    */
  def run(args: List[String]): IO[ExitCode] =
    val consumerAmount = args.headOption
      .flatMap(_.toIntOption)
      .getOrElse(DefaultProcessingConsumerQuantity)
    mainProgramHandler(consumerAmount).as(ExitCode.Success)
  end run

  override def createConsumer(
      channel: Channel
  ): IO[DefaultConsumer] =
    IO.delay(DatabaseConsumer(channel))
end DatabaseCluster
