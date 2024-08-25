import pika

connection = pika.BlockingConnection(pika.ConnectionParameters('localhost', 7003))
channel = connection.channel()
# channel.basic_qos(prefetch_count=1)

channel.queue_declare(queue='hello')

def callback(ch, method, properties, body):
    print(" [x] 22222 %r" % body)
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(queue='hello', on_message_callback=callback, auto_ack=False)
channel.start_consuming()
