import pika
import json

# Connection parameters
RABBITMQ_HOST = 'localhost'
RABBITMQ_PORT = 5672
RABBITMQ_QUEUE = 'logs'
RABBITMQ_USER = 'guest'
RABBITMQ_PASS = 'guest'

# Setup credentials
credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)

# Connection setup with credentials
connection_params = pika.ConnectionParameters(
    host=RABBITMQ_HOST,
    port=RABBITMQ_PORT,
    credentials=credentials
)

# Setup connection and channel
connection = pika.BlockingConnection(connection_params)
channel = connection.channel()

# Make sure the queue exists
channel.queue_declare(queue=RABBITMQ_QUEUE, durable=True)

# Define the callback
def callback(ch, method, properties, body):
    try:
        message = json.loads(body)
        print("\n--- New Log Received ---")
        print(f"\tTask ID: {message.get('taskId')}")
        print(f"\tTask Owner ID: {message.get('taskOwnerId')}")
        print(f"\tFile Path: {message.get('filePath')}")
        print(f"\tTimeout: {message.get('timeout')} seconds")

        log_msg = message.get("logMessage")
        if log_msg is not None:
            print(f"\tLog Message: {log_msg}")

        routing_keys = message.get("routingKeys", [])

        if not routing_keys:
            print("\tTask is considered DONE (no routing keys left).")
        else:
            print("\tRouting Keys left:")
            for exchange, key in routing_keys:
                print(f"\t\tExchange: {exchange}, Routing Key: {key}")

    except json.JSONDecodeError:
        print("\tError: Failed to decode JSON, badly formed")

    ch.basic_ack(delivery_tag=method.delivery_tag)

# Start consuming
channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=callback)
print(f"[*] Waiting for messages on queue '{RABBITMQ_QUEUE}'. To exit press CTRL+C")
channel.start_consuming()
