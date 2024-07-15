/** @author
  *   Esteban Gonzalez Ruales
  */

package types

/** The OpaqueTypes object provides the opaque types for the messaging system
  * elements. This is a way to provide a type-safe way to handle the different
  * elements of the messaging system without exposing the actual type of the
  * elements. This makes the code more robust and less error-prone.
  */
object OpaqueTypes:

  /** The RoutingKey type represents the routing key for the messaging system.
    */
  opaque type RoutingKey = String
  object RoutingKey:
    /** The apply method creates a new RoutingKey.
      *
      * @param value
      *   the value to use
      * @return
      *   a new RoutingKey
      */
    def apply(value: String): RoutingKey = value

    /** The value method returns the value of the RoutingKey.
      *
      * @param rk
      *   the RoutingKey
      * @return
      *   the value
      */
    extension (rk: RoutingKey) def value: String = rk

  /** The ExchangeName type represents the exchange name for the messaging
    * system.
    */
  opaque type ExchangeName = String
  object ExchangeName:
    /** The apply method creates a new ExchangeName.
      *
      * @param value
      *   the value to use
      * @return
      *   a new ExchangeName
      */
    def apply(value: String): ExchangeName = value

    /** The value method returns the value of the ExchangeName.
      *
      * @param en
      *   the ExchangeName
      * @return
      *   the value
      */
    extension (en: ExchangeName) def value: String = en

  /** The QueueName type represents the queue name for the messaging system.
    */
  opaque type QueueName = String
  object QueueName:
    /** The apply method creates a new QueueName.
      *
      * @param value
      *   the value to use
      * @return
      *   a new QueueName
      */
    def apply(value: String): QueueName = value

    /** The value method returns the value of the QueueName.
      *
      * @param qn
      *   the QueueName
      * @return
      *   the value
      */
    extension (qn: QueueName) def value: String = qn
