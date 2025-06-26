import pika
import json

# Number of messages to publish
N = int(input("Enter the number of messages to publish: "))

# Connection parameters
RABBITMQ_HOST = 'localhost'
RABBITMQ_PORT = 5672
RABBITMQ_USER = 'guest'
RABBITMQ_PASS = 'guest'
RABBITMQ_EXCHANGE = 'processing-exchange'
RABBITMQ_QUEUE = 'processing-queue'
ROUTING_KEY = 'task.process'

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

# Ensure the queue exists
channel.queue_declare(queue=RABBITMQ_QUEUE, durable=True)

# Publish N messages
for i in range(1, N + 1):
    task = {
        "taskId": f"{10000 + i}",
        "taskOwnerId": f"{i % 100}",
        "filePath": f"/ftp/one/empty_zip_files/zip_{i}.zip",
        "timeout": 1,
        "routingTree": {
            "exchange": "processing-exchange",
            "routingKey": "skip",
            "successRoutingDecision": {
                "exchange": "processing-exchange",
                "routingKey": "task.done"
            },
            "failureRoutingDecision": {
                "exchange": "processing-exchange",
                "routingKey": "task.failed"
            }
        }
    }

    body = json.dumps(task)
    channel.basic_publish(
        exchange=RABBITMQ_EXCHANGE,
        routing_key=ROUTING_KEY,
        body=body,
        properties=pika.BasicProperties(
            delivery_mode=2  # make message persistent
        )
    )

    print(f"[x] Sent task {i}")

# Cleanup
connection.close()
