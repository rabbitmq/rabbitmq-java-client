# Running RabbitMQ Java Client Test Suites

There are multiple test suites in the RabbitMQ Java client library;
the source for all of the suites can be found in the [src/test/java](src/test/java)
directory.

The suites are:

  * Client tests
  * Server tests
  * TLS connectivity tests
  * Functional tests
  * Multi-node tests

All of them assume a RabbitMQ node listening on `localhost:5672`
(the default settings). TLS tests require a broker listening on the default
TLS port, `5671`. Multi-node tests expect a second cluster node listening on `localhost:5673`.

Connection recovery tests need `rabbitmqctl` to control the running nodes.

Note running all those tests requires a fairly complicated setup and is overkill
for most contributions. This is why this document will cover how to run the most
important subset of the test suite. Continuous integration jobs run the whole test
suite anyway.

## Running Tests

Use `make deps` to fetch the dependencies in the `deps` directory:

```
make deps
```

To run a subset of the test suite (do not forget to start a local RabbitMQ node):

```
./mvnw verify -P '!setup-test-cluster' \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=ClientTestSuite,FunctionalTestSuite,ServerTestSuite
```

The test suite subset does not include TLS tests, which is fine for most
contributions and makes the setup easier.

The previous command launches tests against the blocking IO connector.
To run the tests against the NIO connector, add `-P use-nio` to the command line:

```
./mvnw verify -P '!setup-test-cluster',use-nio \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=ClientTestSuite,FunctionalTestSuite,ServerTestSuite
```

For details on running specific tests, see below.


## Running a Specific Test Suite

To run a specific test suite, execute one of the following in the
top-level directory of the source tree:

* To run the client unit tests:

```
./mvnw verify -P '!setup-test-cluster',use-nio \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=ClientTestSuite
```

* To run the functional tests:

```
./mvnw verify -P '!setup-test-cluster',use-nio \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=FunctionalTestSuite
```

* To run a single test:

```
./mvnw verify -P '!setup-test-cluster',use-nio \
       -Dtest-broker.A.nodename=rabbit@$(hostname) \
       -Drabbitmqctl.bin=/path/to/rabbitmqctl \
       -Dit.test=DeadLetterExchange
```

Test reports can be found in `target/failsafe-reports`.

## Running Against a Broker in a Docker Container

Run the broker:

```
docker run -it --rm --name rabbitmq -p 5672:5672 rabbitmq:3.8
```

Launch the tests:

```
./mvnw verify -P '!setup-test-cluster' \
    -Drabbitmqctl.bin=DOCKER:rabbitmq \
    -Dit.test=ClientTestSuite,FunctionalTestSuite,ServerTestSuite
```

Note the `rabbitmqctl.bin` system property uses the syntax
`DOCKER:{containerId}`.
