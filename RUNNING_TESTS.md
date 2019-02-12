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

`./mvnw verify` will start those nodes with the appropriate configuration.

To easily fulfill all those requirements, you should use `make deps` to
fetch the dependencies in the `deps` directory.

You then run Maven with the `deps.dir` property set like this:
```
./mvnw -Ddeps.dir=$(pwd)/deps verify
```

The previous command launches tests against the blocking IO connector. If you want
to run the tests against the NIO connector, add `-P use-nio` to the command line:

```
./mvnw -Ddeps.dir=$(pwd)/deps verify -P use-nio
```

For details on running specific tests, see below.


## Running a Specific Test Suite

To run a specific test suite, execute one of the following in the
top-level directory of the source tree:

* To run the client unit tests:

 ```
./mvnw -Ddeps.dir=$(pwd)/deps verify -Dit.test=ClientTests
```

* To run the functional tests:

 ```
./mvnw -Ddeps.dir=$(pwd)/deps verify -Dit.test=FunctionalTests
```

* To run a single test:

```
./mvnw -Ddeps.dir=$(pwd)/deps verify -Dit.test=DeadLetterExchange
```

When running from the repository cloned as part of the [RabbitMQ public umbrella](https://github.com/rabbitmq/rabbitmq-public-umbrella),
the `deps.dir` property path may have to change, e.g.

```
./mvnw -Ddeps.dir=$(pwd)/.. verify -Dit.test=ConnectionRecovery
```

For example, to run the client tests:

```
rabbitmq-java-client$ ./mvnw -Ddeps.dir=$(pwd)/deps verify -Dit.test=ClientTests
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building RabbitMQ Java Client 5.3.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (generate-amqp-sources) @ amqp-client ---
[INFO]
[INFO] --- build-helper-maven-plugin:1.12:add-source (add-generated-sources-dir) @ amqp-client ---
[INFO] Source directory: .../rabbitmq-java-client/target/generated-sources/src/main/java added.
[INFO]
[INFO] --- maven-resources-plugin:3.0.2:resources (default-resources) @ amqp-client ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 2 resources
[INFO]
[INFO] --- maven-compiler-plugin:3.6.1:compile (default-compile) @ amqp-client ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] --- maven-bundle-plugin:3.2.0:manifest (bundle-manifest) @ amqp-client ---
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
[INFO] --- maven-resources-plugin:3.0.2:testResources (default-testResources) @ amqp-client ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 5 resources
[INFO]
[INFO] --- maven-compiler-plugin:3.6.1:testCompile (default-testCompile) @ amqp-client ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] --- maven-surefire-plugin:2.19.1:test (default-test) @ amqp-client ---
[INFO] Tests are skipped.
[INFO]
[INFO] --- maven-jar-plugin:3.0.2:jar (default-jar) @ amqp-client ---
[INFO] Building jar: .../rabbitmq-java-client/target/amqp-client-5.3.0-SNAPSHOT.jar
[INFO]
[INFO] >>> maven-source-plugin:3.0.1:jar (default) > generate-sources @ amqp-client >>>
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (generate-amqp-sources) @ amqp-client ---
[INFO]
[INFO] --- build-helper-maven-plugin:1.12:add-source (add-generated-sources-dir) @ amqp-client ---
[INFO] Source directory: .../rabbitmq-java-client/target/generated-sources/src/main/java added.
[INFO]
[INFO] <<< maven-source-plugin:3.0.1:jar (default) < generate-sources @ amqp-client <<<
[INFO]
[INFO]
[INFO] --- maven-source-plugin:3.0.1:jar (default) @ amqp-client ---
[INFO] Building jar: .../rabbitmq-java-client/target/amqp-client-5.3.0-SNAPSHOT-sources.jar
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
Tests run: 121, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 58.869 sec - in com.rabbitmq.client.test.ClientTests

Results :

Tests run: 121, Failures: 0, Errors: 0, Skipped: 0

[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (stop-test-broker-B) @ amqp-client ---
[INFO]
[INFO] --- groovy-maven-plugin:2.0:execute (stop-test-broker-A) @ amqp-client ---
[INFO]
[INFO] --- maven-failsafe-plugin:2.19.1:verify (verify) @ amqp-client ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 01:31 min
[INFO] Finished at: 2018-04-25T11:33:54+02:00
[INFO] Final Memory: 26M/336M
[INFO] ------------------------------------------------------------------------
```

Test reports can be found in `target/failsafe-reports`.


## Running tests against an externally provided broker or cluster

By default, if the RabbitMQ broker sources are available, the testsuite
starts automatically a cluster of two RabbitMQ nodes and runs the tests
against it.

You can also provide your own broker or cluster. To disable the
automatic test cluster setup, disable the `setup-test-cluster` Maven
profile:

```
mvn verify -P '!setup-test-cluster'
```

Note that by doing so some tests will fail as they require `rabbitmqctl` to
control the running nodes.
