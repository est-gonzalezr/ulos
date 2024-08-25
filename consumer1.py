import pika
import time

connection = pika.BlockingConnection(pika.ConnectionParameters('localhost', 7001))
channel = connection.channel()

channel.queue_declare(queue='hello')
channel.basic_qos(prefetch_count=1)

def callback(ch, method, properties, body):
    print(f"waiting 5 seconds")
    time.sleep(5)
    print(" [x] 11111 %r" % body)
    # ack
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(queue='hello', on_message_callback=callback, auto_ack=False)
channel.start_consuming()
