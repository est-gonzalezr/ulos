/** @author
  *   Esteban Gonzalez Ruales
  */

package types

/** Provides the opaque types for the messaging system elements. This is a way
  * to provide a type-safe way to handle the different elements of the messaging
  * system without exposing the actual type of the elements. This makes the code
  * more robust and less error-prone.
  */
case object OpaqueTypes:

  /** Represents the routing key for the messaging system.
    */
  opaque type RoutingKey = String
  object RoutingKey:
    /** Creates a new RoutingKey.
      *
      * @param value
      *   The value to use
      * @return
      *   A new RoutingKey
      */
    def apply(value: String): RoutingKey = value

    /** Returns the value of the RoutingKey.
      *
      * @param rk
      *   The RoutingKey
      * @return
      *   The value
      */
    extension (rk: RoutingKey) def value: String = rk
  end RoutingKey

  /** Represents the exchange name for the messaging system.
    */
  opaque type ExchangeName = String
  object ExchangeName:
    /** Creates a new ExchangeName.
      *
      * @param value
      *   The value to use
      * @return
      *   A new ExchangeName
      */
    def apply(value: String): ExchangeName = value

    /** Returns the value of the ExchangeName.
      *
      * @param en
      *   The ExchangeName
      * @return
      *   The value
      */
    extension (en: ExchangeName) def value: String = en
  end ExchangeName

  /** Represents the queue name for the messaging system.
    */
  opaque type QueueName = String
  object QueueName:
    /** Creates a new QueueName.
      *
      * @param value
      *   The value to use
      * @return
      *   A new QueueName
      */
    def apply(value: String): QueueName = value

    /** Returns the value of the QueueName.
      *
      * @param qn
      *   The QueueName
      * @return
      *   The value
      */
    extension (qn: QueueName) def value: String = qn
  end QueueName
end OpaqueTypes
