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
    <version>5.7.3</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:5.7.3'
```

#### 4.x Series

This client releases are independent from RabbitMQ server releases and can be used with RabbitMQ server `3.x`.
They require Java 6 or higher.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>4.11.3</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:4.11.3'
```


## Contributing

See [Contributing](./CONTRIBUTING.md) and [How to Run Tests](./RUNNING_TESTS.md).


## License

This package, the RabbitMQ Java client library, is triple-licensed under
the Mozilla Public License 1.1 ("MPL"), the GNU General Public License
version 2 ("GPL") and the Apache License version 2 ("ASL").
