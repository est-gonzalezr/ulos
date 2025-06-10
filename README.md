# ULOS

## Temporary instructions to run the system

In your system you will have to have installed `scala3`, `mill`, `python3` and `docker`.

Mill can be installed with the following command:

```zsh
# linux or mac
curl -L https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/1.0.0-RC1/mill-dist-1.0.0-RC1-mill.sh -o mill
chmod +x mill

# windows
curl -L https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/1.0.0-RC1/mill-dist-1.0.0-RC1-mill.bat -o mill.bat
```

Create RabbitMQ container:

```zsh
docker run -d --hostname rabbit --name RabbitMqServer -p 15672:15672 -p 5672:5672 rabbitmq:management
# docker run -d --rm --hostname rabbit --name rabbit -p 15672:15672 -p 5672:5672 rabbitmq:management
```

In your browser go to the url: http://localhost:15672.
Enter with the username: `guest` and password `guest`.

Create a new queue called `processing-queue`.
Create a new exchange called `processing-exchange`.
Click in the new exchange and bind the queue with the exchange with the routing key `processing`.

Create local postgres database:

```zsh
docker run -d --hostname postgres --name PostgresDb -p 5432:5432 -e POSTGRES_PASSWORD=guest postgres:latest
```

Create local ftp server container:

```zsh
docker run -d --hostname delfer --name DelferFtpServer -p 21:21 -p 21000-21010:21000-21010 -e USERS="one|123" -e ADDRESS=localhost delfer/alpine-ftp-server:latest
# docker run -d --rm --hostname delfer --name DelferFtpServer -p 21:21 delfer/alpine-ftp-server:latest
```

From an FTP client connect to the server.
Enter with localhost: `localhost`, username: `one`, password: `123`, and port: `21`.

Upload any file to the server with name `cypressParsing.tar`.
Upload any file to the server with name `cypressExecution.tar`.

In the `testing-files` folder you can find a cypress folder. Compress it into a zip as `task1.zip` and upload it to the FTP server.

In ULOS root folder run:

```zsh
./mill cluster.run
# mill -w brokerManagement.run
```

Once the systems runs, go to the RabbitMQ management url and click on the created queue.
In the "publish message" section of the queue publish a message. A sample message can be found in the ULOS repo on the file: `testing-files/goodJson.json`.

Once you publish the message, the system should immediately process the message.
