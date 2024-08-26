import pika
import time

connection = pika.BlockingConnection(pika.ConnectionParameters('localhost', 5672))
channel = connection.channel()

channel.basic_qos(prefetch_count=1)

def callback(ch, method, properties, body):
    print(f"waiting 5 seconds")
    time.sleep(5)
    print(" [x] 11111 %r" % body)
    # ack
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(queue='parsing_queue', on_message_callback=callback, auto_ack=False)
channel.start_consuming()
