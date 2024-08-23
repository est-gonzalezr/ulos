import cats.effect.IO

import startup.ConsumerProgram
import com.rabbitmq.client.Channel

val DefaultProcessingConsumerQuantity = 1

object DatabaseCluster extends ConsumerProgram:
  @main def main = mainProgram(5)

  override def createConsumer(channel: Channel): IO[DatabaseConsumer] =
    IO(DatabaseConsumer(channel))
end DatabaseCluster
