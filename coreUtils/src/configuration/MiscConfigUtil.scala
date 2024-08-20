/** @author
  *   Esteban Gonzalez Ruales
  */

package configuration

import cats.effect.IO

import ExternalResources.environmentVariableMap

/** Provides utility functions that are not necessarily related to the message
  * broker configuration but are used in the configuration process.
  */
case object MiscConfigUtil:
  /** Reads the environment variables required to configure the message broker
    * and returns them as a map.
    *
    * @return
    *   An IO monad with the environment variables as a map
    */
  def brokerEnvVars: IO[Map[String, String]] =
    environmentVariableMap.use(envMap =>
      for
        rabbitmqHost <- IO.fromOption(envMap.get("RABBITMQ_HOST"))(
          Exception("RABBITMQ_HOST not found in environment variables")
        )
        rabbitmqPort <- IO.fromOption(envMap.get("RABBITMQ_PORT"))(
          Exception("RABBITMQ_PORT not found in environment variables")
        )
        rabbitmqUser <- IO.fromOption(envMap.get("RABBITMQ_USER"))(
          Exception("RABBITMQ_USER not found in environment variables")
        )
        rabbitmqPass <- IO.fromOption(envMap.get("RABBITMQ_PASS"))(
          Exception("RABBITMQ_PASS not found in environment variables")
        )
      yield Map(
        "host" -> rabbitmqHost,
        "port" -> rabbitmqPort,
        "user" -> rabbitmqUser,
        "pass" -> rabbitmqPass
      )
    )

  /** Reads the environment variables required to configure the FTP server and
    * returns them as a map.
    *
    * @return
    *   An IO monad with the environment variables as a map
    */
  def ftpEnvVars: IO[Map[String, String]] =
    environmentVariableMap.use(envMap =>
      for
        ftpHost <- IO.fromOption(envMap.get("FTP_HOST"))(
          Exception("FTP_HOST not found in environment variables")
        )
        ftpPort <- IO.fromOption(envMap.get("FTP_PORT"))(
          Exception("FTP_PORT not found in environment variables")
        )
        ftpUser <- IO.fromOption(envMap.get("FTP_USER"))(
          Exception("FTP_USER not found in environment variables")
        )
        ftpPass <- IO.fromOption(envMap.get("FTP_PASS"))(
          Exception("FTP_PASS not found in environment variables")
        )
      yield Map(
        "host" -> ftpHost,
        "port" -> ftpPort,
        "user" -> ftpUser,
        "pass" -> ftpPass
      )
    )

  /** Reads the environment variables required to configure the consumption
    * queue
    *
    * @return
    *   An IO monad with the consumption queue as a string
    */
  def consumptionQueueEnvVar: IO[String] =
    environmentVariableMap.use(envMap =>
      for consumptionQueue <- IO.fromOption(envMap.get("CONSUMPTION_QUEUE"))(
          Exception("CONSUMPTION_QUEUE not found in environment variables")
        )
      yield consumptionQueue
    )

  /** Reads the environment variables required to configure the routing keys and
    * returns them as a map.
    *
    * @return
    *   An IO monad with the environment variables as a map
    */
  def routingKeysEnvVars: IO[Map[String, String]] =
    environmentVariableMap.use(envMap =>
      for
        publishingRoutingKey <- IO.fromOption(
          envMap.get("PUBLISHING_ROUTING_KEY")
        )(
          Exception("PUBLISHING_ROUTING_KEY not found in environment variables")
        )
        databaseRoutingKey <- IO.fromOption(envMap.get("DATABASE_ROUTING_KEY"))(
          Exception("DATABASE_ROUTING_KEY not found in environment variables")
        )
      yield Map(
        "publishing_routing_key" -> publishingRoutingKey,
        "database_routing_key" -> databaseRoutingKey
      )
    )

  /** Reads the environment variables required to configure the primary exchange
    * and returns it as a string.
    *
    * @return
    *   An IO monad with the primary exchange as a string
    */
  def primaryExchangeEnvVar: IO[String] =
    environmentVariableMap.use(envMap =>
      for primaryExchange <- IO.fromOption(envMap.get("PRIMARY_EXCHANGE"))(
          Exception("PRIMARY_EXCHANGE not found in environment variables")
        )
      yield primaryExchange
    )
