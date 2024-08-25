import pika
import yaml

connection = pika.BlockingConnection(pika.ConnectionParameters('localhost', 5672))
channel = connection.channel()

# channel.queue_declare(queue='hello')
# channel.basic_qos(prefetch_count=1, global_qos=True)

with open("sampleMessage.yaml", "r") as f:
    data = yaml.safe_load(f)
    # to bytes
    data = yaml.dump(data).encode('utf-8')

channel.basic_publish(exchange='processing_exchange', routing_key='task.parsing', body=data)

print(" [x] Sent Message'")
