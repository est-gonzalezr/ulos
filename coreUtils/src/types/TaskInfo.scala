/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import org.virtuslab.yaml.YamlCodec

/** Represents the information of a task.
  *
  * @param taskId
  *   The id of the task
  * @param taskOwnerId
  *   The id of the owner of the task
  * @param storageTaskPath
  *   The path where the task is stored
  * @param storageResultPath
  *   The path where the result of the task is stored
  * @param isParsed
  *   A boolean that indicates if the task has been tried to parse
  * @param isExecuted
  *   A boolean that indicates if the task has been tried to execute
  * @param state
  *   The state of the task
  * @param message
  *   The message of the task
  */
case class TaskInfo(
    taskId: String,
    taskOwnerId: String,
    storageTaskPath: String,
    storageResultPath: Option[String],
    isParsed: Boolean,
    isExecuted: Boolean,
    state: String,
    message: String
) derives YamlCodec
