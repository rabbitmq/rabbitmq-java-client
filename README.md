# RabbitMQ Java Client

This repository contains source code of the [RabbitMQ Java client](http://www.rabbitmq.com/api-guide.html).
The client is maintained by the [RabbitMQ team at Pivotal](http://github.com/rabbitmq/).


## Dependency (Maven Artifact)

Maven artifacts are [released to Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.rabbitmq%20a%3Aamqp-client)
via [RabbitMQ Maven repository on Bintray](https://bintray.com/rabbitmq/maven). There's also
a [Maven repository with milestone releases](https://bintray.com/rabbitmq/maven-milestones).

### Maven

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rabbitmq/amqp-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rabbitmq/amqp-client)

#### 4.x Series

Starting with `4.0`, this client releases are independent from RabbitMQ server releases.
These versions can still be used with RabbitMQ server `3.x`.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:4.0.0'
```

#### 3.6.x Series

`3.6.x` series are released in concert with RabbitMQ server for historical reasons.

``` xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>3.6.6</version>
</dependency>
```

### Gradle

``` groovy
compile 'com.rabbitmq:amqp-client:3.6.6'
```


## Contributing

See [Contributing](./CONTRIBUTING.md) and [How to Run Tests](./RUNNING_TESTS.md).


## License

This package, the RabbitMQ Java client library, is triple-licensed under
the Mozilla Public License 1.1 ("MPL"), the GNU General Public License
version 2 ("GPL") and the Apache License version 2 ("ASL").
