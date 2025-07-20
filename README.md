# ULOS (Uniandes Laboratories Orchestration System)

This project implements a distributed, actor-based system for executing multistage tasks in an asynchronous and fault-tolerant manner. The architecture is designed to handle heterogeneous workloads using an extensible execution model, where tasks are dynamically routed through different processing stages based on outcome and metadata.

Built with Scala and Pekko (a fork of Akka), the system leverages message-driven concurrency and RabbitMQ as the communication backbone between distributed nodes.

For a deeper understanding, refer to the master's thesis [Architectural Foundations for Multistage Task Orchestration in Educational Systems]() that lays the motivation and theoretical foundations of the project.

# Requirements

To develop and run this project, the following tools must be installed on your system:

- **Scala**

  The project is implemented in Scala.

  To install Scala, visit [Scala](https://www.scala-lang.org/) and follow the installation instructions. Make sure that the Scala version installed is compatible with the version that the project is using. You can check the project's Scala version by checking the `build.mill` file.

- **Mill**

  The build tool used is Mill.

  To install mill, visit [Mill](https://mill-build.org/) and follow the installation instructions. Make sure that the Mill version installed is compatible with the version that the project is using. You can check the project's Mill version by checking the `build.mill` file.

- **Java**

  The system uses Java for some of its components and as the JVM runtime environment.

  To install Java, visit [Java](https://www.java.com/) and follow the installation instructions.

- **Python**

  The system uses Python scripts to help with testing and development.

  To install Python, visit [Python](https://www.python.org/) and follow the installation instructions.

# Runtime Dependencies

To be able to run the system, some external dependencies are required:

- **Docker**

  Currently some of the executors run in Docker containers. To install Docker, visit [Docker](https://www.docker.com/) and follow the installation instructions.

- **RabbitMQ**

  RabbitMQ is the default message broker implementation used by the system. To install RabbitMQ, visit [RabbitMQ](https://www.rabbitmq.com/) and follow the installation instructions. The RabbitMQ instance does not need to be running on the same machine as the system, but is must be reachable from the system. For development purposes, you can run RabbitMQ in a Docker container using the following command:

  ```zsh
  docker run -d --hostname rabbit --name RabbitMqServer -p 15672:15672 -p 5672:5672 rabbitmq:management
  ```

  For production purposes, a dedicated server is recommended with a custom username and password.

- **FTP Server**

  FTP is the default file transfer protocol used by the system. To install FTP, choose a suitable FTP server software and follow the installation instructions. The FTP server does not need to be running on the same machine as the system, but is must be reachable from the system. For development purposes, you can run FTP in a Docker container using the following command:

  ```zsh
  docker run -d --hostname delfer --name DelferFtpServer -p 21:21 -p 21000-21010:21000-21010 -e USERS="one|123" -e ADDRESS=localhost delfer/alpine-ftp-server:latest
  ```

  For production purposes, a dedicated server is recommended with a custom username and password.

# Compiling and Running

To run the system, regardless of the environment, it is necessary to setup the environment variables for the system. An example of how to do this is shown below:

```zsh
export MESSAGE_BROKER_HOST=localhost
export MESSAGE_BROKER_PORT=5672
export MESSAGE_BROKER_USERNAME=guest
export MESSAGE_BROKER_PASSWORD=guest
export MESSAGE_BROKER_LOGS_EXCHANGE=processing-exchange
export MESSAGE_BROKER_LOGS_ROUTING_KEY=task.log
export MESSAGE_BROKER_CRASH_EXCHANGE=processing-exchange
export MESSAGE_BROKER_CRASH_ROUTING_KEY=task.crashed
export MESSAGE_BROKER_CONSUMPTION_QUEUE=processing-queue
export MESSAGE_BROKER_PREFETCH_COUNT=0
export MESSAGE_BROKER_REQUEUE_ON_REJECT=false
export REMOTE_STORAGE_HOST=localhost
export REMOTE_STORAGE_PORT=21
export REMOTE_STORAGE_USERNAME=one
export REMOTE_STORAGE_PASSWORD=123
```

Each environment variable has its own purpose and should be set accordingly for the system to function correctly.

| Variable                           | Description                                                       |
| ---------------------------------- | ----------------------------------------------------------------- |
| `MESSAGE_BROKER_HOST`              | The hostname of the message broker server.                        |
| `MESSAGE_BROKER_PORT`              | The port number of the message broker server.                     |
| `MESSAGE_BROKER_USERNAME`          | The username for the message broker server.                       |
| `MESSAGE_BROKER_PASSWORD`          | The password for the message broker server.                       |
| `MESSAGE_BROKER_LOGS_EXCHANGE`     | The name of the message broker exchange for log-level messages.   |
| `MESSAGE_BROKER_LOGS_ROUTING_KEY`  | The routing key for log-level messages emitted by the system.     |
| `MESSAGE_BROKER_CRASH_EXCHANGE`    | The name of the message broker exchange for crash-level messages. |
| `MESSAGE_BROKER_CRASH_ROUTING_KEY` | The routing key for crash-level messages emitted by the system.   |
| `MESSAGE_BROKER_CONSUMPTION_QUEUE` | The name of the queue for consuming messages.                     |
| `MESSAGE_BROKER_PREFETCH_COUNT`    | The number of messages to prefetch from the queue.                |
| `MESSAGE_BROKER_REQUEUE_ON_REJECT` | Whether to requeue messages that are rejected.                    |
| `REMOTE_STORAGE_HOST`              | The hostname of the remote storage server.                        |
| `REMOTE_STORAGE_PORT`              | The port number of the remote storage server.                     |
| `REMOTE_STORAGE_USERNAME`          | The username for the remote storage server.                       |
| `REMOTE_STORAGE_PASSWORD`          | The password for the remote storage server.                       |

Some variables are not as self-explanatory so a more thorough explanation of some concepts and variables is provided.

The systems emits some messages that are not meant to be consumed by the system itself but rather directed to other systems. Such messages are the Log Messages and the Crash Messages. Log Messages serve as a way to track the progress of a task within the system. These messages are published to the `MESSAGE_BROKER_LOGS_EXCHANGE` exchange with the routing key `MESSAGE_BROKER_LOGS_ROUTING_KEY` so that another system can consume them and decide what to do with them. The Crash Messages serve as a way to notify other systems of a task-induced crash within the system. Unless catastrophic, these crashes don't crash the system and are just logged. These messages are published to the `MESSAGE_BROKER_CRASHES_EXCHANGE` exchange with the routing key `MESSAGE_BROKER_CRASHES_ROUTING_KEY` so that another system can consume them and decide what to do with them.

The `MESSAGE_BROKER_CONSUMPTION_QUEUE` is a queue that specifies from which RabbitMQ queue the system will consume messages from. Since the system can be deployed multiple times, multiple instances of the system can consume messages from the same queue. Since the idea of the system is to process a variety of tasks, multiple instances of the system can consume from different queues to diversify task type processing. The `MESSAGE_BROKER_PREFETCH_COUNT` is the number of messages that the system will prefetch from the RabbitMQ queue without them being acknowledged. This needs to be fine tuned by the system administrator to optimize performance based on the system's load and the nature of the tasks being processed. The `MESSAGE_BROKER_REQUEUE_ON_REJECT` is a boolean flag that specifies whether the system should requeue messages that are rejected by the system. This flag should be left on `false` if the RabbitMQ server is not configured with dead-letter queues and ways to identify consistently rejected messages. More information on how to configure this can be found in the [RabbitMQ Documentation](https://www.rabbitmq.com/docs). Under normal circumstances, messages that are rejected by the system have an an underlying crash and so will be sent to the crash exchange with the crash routing key; a consuming system can determine what to do with them.

## Compiling

In order to compile the system, you can run the following Mill command:

```zsh
./mill cluster.assembly
```

This command will create a `.jar` file that will normally be stored under `/ulos/out/cluster/assembly.dest/` with name `out.jar`.

## Running

You can run the jar file using the following command:

```zsh
java -jar out.jar
```

If you want to run the project without having to compile it, you can use the following command:

```zsh
./mill cluster.run
```

Remember to configure the environment variables and have a running RabbitMQ and FTP server before running the system.

# Usage and Considerations

Inside this project there will be some folders that will help you understand the system and get it up and running.

The `testing-configurations` folder contains files that have multiple examples of environment-variable configurations to showcase different configurations that a system can have.

The `testing-files` folder contains files that have multiple examples of files that can be uploaded to the FTP server and that the system can process.

The `testing-messages` folder contains files that have multiple examples of messages that can be sent to the system via the RabbitMQ server. Each of these messages can be placed in the queue that the system is consuming from and it will be processed.

The `python-helpers` folder contains files that have multiple examples of Python scripts that can be used to help you test the system.

## Message Schema

Each of the messsages sent to the system has a certain schema that must be followed for the system to be able to process them correctly. A message examples is shown below:

```json
{
  "taskId": "12a345",
  "taskOwnerId": "67",
  "filePath": "/ftp/one/testing.zip",
  "timeout": 0,
  "routingTree": {
    "exchange": "processing-exchange",
    "routingKey": "pass",
    "successRoutingDecision": {
      "exchange": "processing-exchange",
      "routingKey": "task.unexpected"
    },
    "failureRoutingDecision": {
      "exchange": "processing-exchange",
      "routingKey": "task.failed"
    }
  }
}
```

| Field         | Type   | Description                                                              |
| ------------- | ------ | ------------------------------------------------------------------------ |
| `taskId`      | string | The unique identifier of the task in the system.                         |
| `taskOwnerId` | string | The unique identifier of the owner of the task in the system.            |
| `filePath`    | string | The FTP path to the file that will be processed by the system.           |
| `timeout`     | number | The amount of time in seconds the system will wait before timing out.    |
| `routingTree` | object | The routing tree used to route the task to the appropriate exchange/key. |

The `routingTree` is recursive structure that defines the routing path for the task across the system. For example, this can be an example of a task that has more that one routing step:

```json
{
  "taskId": "12345",
  "taskOwnerId": "67",
  "filePath": "/ftp/one/testing.zip",
  "timeout": 10,
  "routingTree": {
    "exchange": "processing-exchange",
    "routingKey": "testing-1",
    "successRoutingDecision": {
      "exchange": "processing-exchange",
      "routingKey": "testing-2",
      "successRoutingDecision": {
        "exchange": "processing-exchange",
        "routingKey": "crash",
        "successRoutingDecision": {
          "exchange": "processing-exchange",
          "routingKey": "testing-4",
          "successRoutingDecision": {
            "exchange": "processing-exchange",
            "routingKey": "testing-5",
            "successRoutingDecision": {
              "exchange": "processing-exchange",
              "routingKey": "task.done",
              "successRoutingDecision": null,
              "failureRoutingDecision": null
            },
            "failureRoutingDecision": {
              "exchange": "processing-exchange",
              "routingKey": "task.failed",
              "successRoutingDecision": null,
              "failureRoutingDecision": null
            }
          },
          "failureRoutingDecision": {
            "exchange": "processing-exchange",
            "routingKey": "task.failed",
            "successRoutingDecision": null,
            "failureRoutingDecision": null
          }
        },
        "failureRoutingDecision": {
          "exchange": "processing-exchange",
          "routingKey": "task.failed",
          "successRoutingDecision": null,
          "failureRoutingDecision": null
        }
      },
      "failureRoutingDecision": {
        "exchange": "processing-exchange",
        "routingKey": "task.failed",
        "successRoutingDecision": null,
        "failureRoutingDecision": null
      }
    },
    "failureRoutingDecision": {
      "exchange": "processing-exchange",
      "routingKey": "task.failed",
      "successRoutingDecision": null,
      "failureRoutingDecision": null
    }
  }
}
```

Routing steps are not mandatory, so an equally valid example can look as follows:

```json
{
  "taskId": "12345",
  "taskOwnerId": "67",
  "filePath": "/ftp/one/testing.zip",
  "timeout": 10,
  "routingTree": {
    "exchange": "processing-exchange",
    "routingKey": "testing-1",
    "successRoutingDecision": {
      "exchange": "processing-exchange",
      "routingKey": "testing-2",
      "successRoutingDecision": {
        "exchange": "processing-exchange",
        "routingKey": "crash",
        "successRoutingDecision": {
          "exchange": "processing-exchange",
          "routingKey": "testing-4",
          "successRoutingDecision": {
            "exchange": "processing-exchange",
            "routingKey": "testing-5"
          }
        }
      }
    }
  }
}
```

This task only has routing steps if the task is successful. If a task fails (crash is not failure, failure is determined internally by the system), then the task will not be routed to any exchange.

Routing decisions are dynamic and flexible, allowing for complex routing scenarios, but they are completely dependent on the defined routing tree. The system will route messages based on the routing tree on internal success or failure conditions, but will not determine new routing decisions. This allows for deterministic and flexible routing, but also places the routing responsibility on the system administrator and its implementation of systems that will determine the routing decisions. It is worth noting that specifying non-existent routing keys or exchanges will not cause the system to throw any warnings or errors, so messages can be potentially lost if there are no dead-letter queues configured on the message broker.

## RabbitMQ Considerations

Make sure to declare the needed exchanges, queues and bindings, the system assumes that the underlying infrastructure is properly configured and that the exchanges, queues and bindings are correctly declared, and that the system is properly configured to handle routing decisions.

## FTP Considerations

Since there is no automatic upload of files to the FTP server, make sure to upload any file that you intend for the system to process.

## System Considerations

Make sure to never send two messages with the same `ftpPath` to a same node since, based on how the system is designed, two tasks might be accessing information about the same file at the same time, which could lead to data corruption, race conditions, and/or inconsistent state.

## Usage with Executors

To be able to execute the system using custom executors, refer to the [Ulos Executors Repository](https://github.com/NoNameLab/ulos-executors) and follow their instructions to build the custom executors. For Docker related executors, it is necessary to have the Docker daemon running and accessible from the system and for each executor image to be mounted in the system. Also make sure that the name of the image of each executor matches the in Docker and on the corresponding executor inside of the `executors` directory inside of this project.

# Future Work

Future work is included in the master's thesis referenced at the beginning of the README. For future maintainers of the system, please make sure you are familiar with functional programming concepts and the actor model.
