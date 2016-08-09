## Overview

There are multiple test suites in the RabbitMQ Java client library;
the source for all of the suites can be found in the [src/test/java](src/test/java)
directory.

The suites are:

  * Client tests
  * Server tests
  * SSL tests
  * Functional tests
  * HA tests

All of them assume a RabbitMQ node listening on localhost:5672
(the default settings). SSL tests require a broker listening on the default
SSL port. HA tests expect a second node listening on localhost:5673.

Connection recovery tests need `rabbitmqctl` to control the running nodes.
can control the running node.

`mvn verify` will start those nodes with the appropriate configuration.

To easily fullfil all those requirements, you can use `make deps` to
fetch the dependencies. You can also fetch them yourself and use the
same layout:

```
deps
|-- rabbitmq_codegen
`-- rabbit
```

You then run Maven with the `deps.dir` property set like this:
```
mvn -Ddeps.dir=/path/to/deps verify
```

For details on running specific tests, see below.


## Running a Specific Test Suite

To run a specific test suite you should execute one of the following in the
top-level directory of the source tree:

* To run the client unit tests:

 ```
mvn -Ddeps.dir=/path/to/deps verify -Dit.test=ClientTests
```

* To run the functional tests:

 ```
mvn -Ddeps.dir=/path/to/deps verify -Dit.test=FunctionalTests
```

* To run a single test:

 ```
mvn -Ddeps.dir=/path/to/deps verify -Dit.test=DeadLetterExchange
```

For example, to run the client tests:

```
rabbitmq-java-client$ mvn -Ddeps.dir=/path/to/deps verify -Dit.test=ClientTests
[INFO] Scanning for projects...
[INFO] Inspecting build with total of 1 modules...
[INFO] Installing Nexus Staging features:
[INFO]   ... total of 1 executions of maven-deploy-plugin replaced with nexus-staging-maven-plugin
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building RabbitMQ Java Client 3.7.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (generate-amqp-sources) @ amqp-client ---
[INFO]
[INFO] --- build-helper-maven-plugin:1.12:add-source (add-generated-sources-dir) @ amqp-client ---
[INFO] Source directory: .../rabbitmq_java_client/target/generated-sources/src/main/java added.
[INFO]
[INFO] --- maven-resources-plugin:2.5:resources (default-resources) @ amqp-client ---
[debug] execute contextualize
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 1 resource
[INFO]
[INFO] --- maven-compiler-plugin:3.5.1:compile (default-compile) @ amqp-client ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (remove-old-test-keystores) @ amqp-client ---
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (query-test-tls-certs-dir) @ amqp-client ---
[INFO]
[INFO] --- keytool-maven-plugin:1.5:importCertificate (generate-test-ca-keystore) @ amqp-client ---
[WARNING] Certificate was added to keystore
[INFO]
[INFO] --- keytool-maven-plugin:1.5:importCertificate (generate-test-empty-keystore) @ amqp-client ---
[WARNING] Certificate was added to keystore
[INFO]
[INFO] --- keytool-maven-plugin:1.5:deleteAlias (generate-test-empty-keystore) @ amqp-client ---
[INFO]
[INFO] --- maven-resources-plugin:2.5:testResources (default-testResources) @ amqp-client ---
[debug] execute contextualize
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 3 resources
[INFO]
[INFO] --- maven-compiler-plugin:3.5.1:testCompile (default-testCompile) @ amqp-client ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] --- maven-surefire-plugin:2.19.1:test (default-test) @ amqp-client ---
[INFO] Tests are skipped.
[INFO]
[INFO] --- maven-jar-plugin:3.0.2:jar (default-jar) @ amqp-client ---
[INFO] Building jar: .../rabbitmq_java_client/target/amqp-client-3.7.0-SNAPSHOT.jar
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (start-test-broker-A) @ amqp-client ---
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (start-test-broker-B) @ amqp-client ---
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (create-test-cluster) @ amqp-client ---
[INFO]
[INFO] --- maven-failsafe-plugin:2.19.1:integration-test (integration-test) @ amqp-client ---

-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running com.rabbitmq.client.test.ClientTests
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.732 sec - in com.rabbitmq.client.test.ClientTests

Results :

Tests run: 50, Failures: 0, Errors: 0, Skipped: 0

[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (stop-test-broker-B) @ amqp-client ---
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (stop-test-broker-A) @ amqp-client ---
[INFO]
[INFO] --- maven-failsafe-plugin:2.19.1:verify (verify) @ amqp-client ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 33.707s
[INFO] Finished at: Mon Aug 08 16:22:26 CEST 2016
[INFO] Final Memory: 21M/256M
[INFO] ------------------------------------------------------------------------
```

Test reports can be found in `target/failsafe-reports`.
