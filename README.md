# RabbitMQ Java Client

This repository contains source code of the [RabbitMQ Java client](https://www.rabbitmq.com/api-guide.html).
The client is maintained by the [RabbitMQ team at Pivotal](https://github.com/rabbitmq/).


## Dependency (Maven Artifact)

Maven artifacts are [released to Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.rabbitmq%20a%3Aamqp-client)
via [RabbitMQ Maven repository on Bintray](https://bintray.com/rabbitmq/maven). There's also
a [Maven repository with milestone releases](https://bintray.com/rabbitmq/maven-milestones). [Snapshots are available](https://oss.sonatype.org/content/repositories/snapshots/com/rabbitmq/amqp-client/) as well.

### Maven

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rabbitmq/amqp-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rabbitmq/amqp-client)

#### 5.x Series

This client releases are independent from RabbitMQ server releases and can be used with RabbitMQ server `3.x`.
They require Java 8 or higher.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>5.2.0</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:5.2.0'
```

#### 4.x Series

This client releases are independent from RabbitMQ server releases and can be used with RabbitMQ server `3.x`.
They require Java 6 or higher.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>4.6.0</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:4.6.0'
```

## Experimenting with JShell

You can experiment with the client from JShell. This requires Java 9 or more.

```
git clone https://github.com/rabbitmq/rabbitmq-java-client.git
cd rabbitmq-java-client
./mvnw test-compile jshell:run
...
import com.rabbitmq.client.*
ConnectionFactory cf = new ConnectionFactory()
Connection c = cf.newConnection()
...
c.close()
/exit
```

## Building from Source

### Getting the Project and its Dependencies

```
git clone git@github.com:rabbitmq/rabbitmq-java-client.git
cd rabbitmq-java-client
make deps
```

### Building the JAR File

```
./mvnw clean package -Dmaven.test.skip -P '!setup-test-cluster'
```

### Launching Tests with the Broker Running In a Docker Container

Run the broker:

```
docker run -it --rm --name rabbitmq -p 5672:5672 rabbitmq:3.8
```

Launch "essential" tests (takes about 10 minutes):

```
./mvnw verify -P '!setup-test-cluster' \
    -Drabbitmqctl.bin=DOCKER:rabbitmq \
    -Dit.test=ClientTests,FunctionalTests,ServerTests
```

Launch a single test:

```
./mvnw verify -P '!setup-test-cluster' \
    -Drabbitmqctl.bin=DOCKER:rabbitmq \
    -Dit.test=DeadLetterExchange
```

### Launching Tests with a Local Broker

The tests can run against a local broker as well. The `rabbitmqctl.bin`
system property must point to the `rabbitmqctl` program:

```
./mvnw verify -P '!setup-test-cluster' \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=ClientTests,FunctionalTests,ServerTests
```

To launch a single test:

```
./mvnw verify -P '!setup-test-cluster' \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=DeadLetterExchange
```

## Contributing

See [Contributing](./CONTRIBUTING.md) and [How to Run Tests](./RUNNING_TESTS.md).


## License

This package, the RabbitMQ Java client library, is [triple-licensed](https://www.rabbitmq.com/api-guide.html#license) under
the Mozilla Public License 2.0 ("MPL"), the GNU General Public License
version 2 ("GPL") and the Apache License version 2 ("ASL").
