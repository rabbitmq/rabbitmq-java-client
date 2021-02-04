# RabbitMQ Java Client

This repository contains source code of the [RabbitMQ Java client](https://www.rabbitmq.com/api-guide.html).
The client is maintained by the [RabbitMQ team at Pivotal](https://github.com/rabbitmq/).


## Dependency (Maven Artifact)

This package is published to several Maven package repositories:

* [Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.rabbitmq%20a%3Aamqp-client)
* [RabbitMQ Maven Milestones repository](https://packagecloud.io/rabbitmq/maven-milestones) 
* [Sonatype OSS snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/com/rabbitmq/jms/rabbitmq-jms/)

### Maven

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rabbitmq/amqp-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rabbitmq/amqp-client)

#### 5.x Series

This client releases are independent from RabbitMQ server releases and can be used with RabbitMQ server `3.x`.
They require Java 8 or higher.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>5.10.0</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:5.10.0'
```

#### 4.x Series

As of 1 January 2021 the 4.x branch is no longer supported.

This client releases are independent from RabbitMQ server releases and can be used with RabbitMQ server `3.x`.
They require Java 6 or higher.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>4.12.0</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:4.12.0'
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

## Contributing

See [Contributing](./CONTRIBUTING.md) and [How to Run Tests](./RUNNING_TESTS.md).

## Versioning

This library uses [semantic versioning](https://semver.org/).

## Support

See the [RabbitMQ Java libraries support page](https://www.rabbitmq.com/java-versions.html)
for the support timeline of this library.

## License

This package, the RabbitMQ Java client library, is [triple-licensed](https://www.rabbitmq.com/api-guide.html#license) under
the Mozilla Public License 2.0 ("MPL"), the GNU General Public License
version 2 ("GPL") and the Apache License version 2 ("ASL").

This means that the user can consider the library to be licensed under **any of the licenses from the list** above.
For example, you may choose the Apache Public License 2.0 and include this client into a commercial product.
Projects that are licensed under the GPLv2 may choose GPLv2, and so on.
