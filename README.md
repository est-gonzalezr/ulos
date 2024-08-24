# ULOS

## Temporary instructions to run the system

In your system you will have to have installed `scala`, `mill`, `python3` and `docker.

Create RabbitMQ container:

```zsh
docker run -d --rm --net rabbits --hostname rabbit_1 --name rabbit_1 -p 15672:15672 -p 5672:5672 -e RABBITMQ_ERLANG_COOKIE=ERLANGCOOKIE rabbitmq:management
```

In ULOS root folder run

```zsh
mill -w brokerManagement.run
```

and select the first option: `1. Configure the message broker from existing yaml files`.

After that, run each of the following commands in different terminal windows.

```zsh
mill -w parsingCluster.run
mill -w executionCluster.run
mill -w databaseCluster.run
```

Finally, run the `producer.py` script to send a mock message, you will need to have `pika` and `PyYAML` installed.

```zsh
python3 producer.py
```
