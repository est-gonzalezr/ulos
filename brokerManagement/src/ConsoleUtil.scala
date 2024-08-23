import cats.effect.IO
import cats.effect.std.Console

/** Provides utility functions to interact with the console.
  */
case object ConsoleUtil:

  /** Prompts the user for input to create a new exchange.
    *
    * @return
    *   An IO monad containing a Map of the exchange's properties
    */
  def newExchangeInput: IO[Map[String, String]] =
    for
      _ <- Console[IO].println("Enter the exchange name:")
      exchangeName <- Console[IO].readLine
      _ <- Console[IO].println("Enter the exchange type:")
      exchangeType <- Console[IO].readLine
      _ <- Console[IO].println("Enter the exchange durability:")
      durable <- Console[IO].readLine
      _ <- Console[IO].println("Enter the exchange autoDelete:")
      autoDelete <- Console[IO].readLine
      _ <- Console[IO].println("Enter the exchange internal:")
      internal <- Console[IO].readLine
    yield Map(
      "exchangeName" -> exchangeName,
      "exchangeType" -> exchangeType,
      "durable" -> durable,
      "autoDelete" -> autoDelete,
      "internal" -> internal
    )

  /** Prompts the user for input to create a new queue.
    *
    * @return
    *   An IO monad containing a Map of the queue's properties
    */
  def newQueueInput: IO[Map[String, String]] =
    for
      _ <- Console[IO].println("Enter the queue name:")
      queueName <- Console[IO].readLine
      _ <- Console[IO].println("Enter the exchange name:")
      exchangeName <- Console[IO].readLine
      _ <- Console[IO].println("Enter the queue durability:")
      durable <- Console[IO].readLine
      _ <- Console[IO].println("Enter the queue exclusive:")
      exclusive <- Console[IO].readLine
      _ <- Console[IO].println("Enter the queue autoDelete:")
      autoDelete <- Console[IO].readLine
      _ <- Console[IO].println("Enter the routing key:")
      routingKey <- Console[IO].readLine
    yield Map(
      "queueName" -> queueName,
      "exchangeName" -> exchangeName,
      "durable" -> durable,
      "exclusive" -> exclusive,
      "autoDelete" -> autoDelete,
      "routingKey" -> routingKey
    )

  /** Prompts the user for input to delete an exchange.
    *
    * @return
    *   An IO monad containing the name of the exchange to delete
    */
  def exchangeDeleteInput: IO[String] =
    for
      _ <- Console[IO].println("Enter the exchange name:")
      exchangeName <- Console[IO].readLine
    yield exchangeName

  /** Prompts the user for input to delete a queue.
    *
    * @return
    *   an IO monad containing the name of the queue to delete
    */
  def queueDeleteInput: IO[String] =
    for
      _ <- Console[IO].println("Enter the queue name:")
      queueName <- Console[IO].readLine
    yield queueName

  /** Contains a list of tuples with the options available to the user.
    *
    * @return
    *   A list of tuples with the options available to the user
    */
  def options: List[(Int, String)] =
    List(
      1 -> "Configure the message broker from existing yaml files",
      2 -> "Add an exchange to the broker",
      3 -> "Add a queue to the broker",
      4 -> "Delete an exchange from the broker",
      5 -> "Delete a queue from the broker"
    )
end ConsoleUtil
