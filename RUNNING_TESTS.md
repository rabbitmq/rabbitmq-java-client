## Overview

There are multiple test suites in the RabbitMQ Java client library;
the source for all of the suites can be found in the [test/src](./test/src)
directory.

The suites are:

  * Client tests
  * Functional tests
  * Server tests
  * SSL tests

All of them assume a RabbitMQ node listening on localhost:5672
(the default settings). SSL tests require a broker listening on the default
SSL port. Connection recovery tests assume `rabbitmqctl` at `../rabbitmq-server/scripts/rabbitmqctl`
can control the running node: this is the case when all repositories are cloned using
the [umbrella repository](https://github.com/rabbitmq/rabbitmq-public-umbrella).

For details on running specific tests, see below.


## Running a Specific Test Suite

To run a specific test suite you should execute one of the following in the
top-level directory of the source tree:

    # runs unit tests
    ant test-client
    # runs integration/functional tests
    ant test-functional
    # runs TLS tests
    ant test-ssl
    # run all test suites
    ant test-suite

For example, to run the client tests:

```
----------------- Example shell session -------------------------------------
rabbitmq-java-client$ ant test-client
Buildfile: build.xml

test-prepare:

test-build:

amqp-generate-check:

amqp-generate:

build:

test-build-param:

test-client:
    [junit] Running com.rabbitmq.client.test.ClientTests
    [junit] Tests run: 31, Failures: 0, Errors: 0, Time elapsed: 2.388 sec

BUILD SUCCESSFUL
-----------------------------------------------------------------------------
```

Test failures and errors are logged to `build/TEST-*`.


## SSL Test Setup

To run the SSL tests, the RabbitMQ server and Java client should be configured
as per the SSL instructions on the RabbitMQ website. The `SSL_CERTS_DIR`
environment variable must point to a certificate folder with the following
minimal structure:

```
   $SSL_CERTS_DIR
   |-- client
   |   |-- keycert.p12
   |   |-- cert.pem
   |   \-- key.pem
   |-- server
   |   |-- cert.pem
   |   \-- key.pem
   \-- testca
       \-- cacert.pem
```

The `PASSWORD` environment variable must be set to the password of the keycert.p12
PKCS12 keystore. The broker must be configured to validate client certificates.
This will become minimal broker configuration file if `$SSL_CERTS_DIR` is replaced
with the certificate folder:

``` erlang
%%%%% begin sample test broker configuration
[{rabbit, [{ssl_listeners, [5671]},
           {ssl_options,   [{cacertfile,"$SSL_CERTS_DIR/testca/cacert.pem"},
                            {certfile,"$SSL_CERTS_DIR/server/cert.pem"},
                            {keyfile,"$SSL_CERTS_DIR/server/key.pem"},
                            {verify,verify_peer},
                            {fail_if_no_peer_cert, false}]}]}].
%%%%% end sample test broker configuration
```
