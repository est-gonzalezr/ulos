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
  def brokerEnvironmentVariables: IO[Map[String, String]] =
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
  def ftpEnvironmentVariables: IO[Map[String, String]] =
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
