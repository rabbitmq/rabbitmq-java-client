package com.rabbitmq.docs;

// tag::imports[]
import com.rabbitmq.client.*;
// end::imports[]

import com.rabbitmq.client.Method;
import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.impl.DefaultCredentialsRefreshService.DefaultCredentialsRefreshServiceBuilder;
import com.rabbitmq.client.impl.nio.NioParams;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.rabbitmq.client.impl.OAuth2ClientCredentialsGrantCredentialsProvider.*;

public class Api {

  void connecting() throws Exception {
    String username = null, password = null, virtualHost = null, hostName = null;
    int portNumber = 5672;
    // tag::connecting[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername(username);  // <1>
    factory.setPassword(password);  // <1>
    factory.setVirtualHost(virtualHost);
    factory.setHost(hostName);
    factory.setPort(portNumber);

    Connection conn = factory.newConnection();
    // end::connecting[]
  }

  void uri() throws Exception {
    // tag::uri[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUri("amqp://username:password@host:port/virtualHost");
    Connection conn = factory.newConnection();
    // end::uri[]
  }

  void openChannel() throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    Connection conn = factory.newConnection();
    // tag::open-channel[]
    Channel channel = conn.createChannel();
    // end::open-channel[]
  }

  void endpointList() throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    String hostname1 = null, hostname2 = null;
    int port1 = 5672, port2 = 5672;
    // tag::endpoint-list[]
    Address[] addrArr = new Address[] {
        new Address(hostname1, port1),
        new Address(hostname2, port2)
    };
    Connection conn = factory.newConnection(addrArr);
    // end::endpoint-list[]
  }

  void disconnecting() throws Exception {
    Connection conn = null;
    Channel channel = null;
    // tag::disconnecting[]
    channel.close();
    conn.close();
    // end::disconnecting[]
  }

  void clientProvidedName() throws Exception {
    // tag::client-provided-name[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUri("amqp://user:password@host:port/vhost");
    Connection conn = factory.newConnection(
        "app:audit component:event-consumer"  // <1>
    );
    // end::client-provided-name[]
  }

  void declareExclusiveQueue() throws Exception {
    Channel channel = null;
    String exchangeName = null;
    String routingKey = null;
    // tag::declare-exclusive-queue[]
    channel.exchangeDeclare(exchangeName, "direct", true);
    String queueName = channel.queueDeclare().getQueue();
    channel.queueBind(queueName, exchangeName, routingKey);
    // end::declare-exclusive-queue[]
  }

  void declareQueue() throws Exception {
    Channel channel = null;
    String exchangeName = null;
    String queueName = null;
    String routingKey = null;
    // tag::declare-queue[]
    channel.exchangeDeclare(exchangeName, "direct", true);
    channel.queueDeclare(queueName, true, false, false, null);
    channel.queueBind(queueName, exchangeName, routingKey);
    // end::declare-queue[]
  }

  void declarePassive() throws Exception {
    Channel channel = null;
    // tag::declare-passive[]
    AMQP.Queue.DeclareOk response = channel.queueDeclarePassive(
        "queue-name"
    );
    response.getMessageCount();  // <1>
    response.getConsumerCount();  // <2>
    // end::declare-passive[]
  }

  void declareNoWait() throws Exception {
    Channel channel = null;
    String queueName = null;
    // tag::declare-no-wait[]
    channel.queueDeclareNoWait(queueName, true, false, false, null);
    // end::declare-no-wait[]
  }

  void delete() throws Exception {
    Channel channel = null;
    // tag::delete[]
    channel.queueDelete("queue-name");
    // end::delete[]
    // tag::delete-empty[]
    channel.queueDelete("queue-name", false, true);
    // end::delete-empty[]
    // tag::delete-unused[]
    channel.queueDelete("queue-name", true, false);
    // end::delete-unused[]
    // tag::purge[]
    channel.queuePurge("queue-name");
    // end::purge[]
  }

  void publish() throws Exception {
    Channel channel = null;
    String exchangeName = null, routingKey = null;
    // tag::publish[]
    byte[] messageBodyBytes = "Hello, world!".getBytes();
    channel.basicPublish(exchangeName, routingKey, null, messageBodyBytes);
    // end::publish[]
    boolean mandatory = true;
    // tag::publish-mandatory[]
    channel.basicPublish(exchangeName, routingKey, mandatory,
                         MessageProperties.PERSISTENT_TEXT_PLAIN,
                         messageBodyBytes);
    // end::publish-mandatory[]
    // tag::publish-builder[]
    channel.basicPublish(
        exchangeName,
        routingKey,
        new AMQP.BasicProperties.Builder()
            .contentType("text/plain")
            .deliveryMode(2)
            .priority(1)
            .userId("bob")
            .build(),
        messageBodyBytes);
    // end::publish-builder[]
    // tag::publish-headers[]
    Map<String, Object> headers = new HashMap<>();
    headers.put("latitude",  51.5252949);
    headers.put("longitude", -0.0905493);

    channel.basicPublish(
        exchangeName,
        routingKey,
        new AMQP.BasicProperties.Builder().headers(headers).build(),
        messageBodyBytes);
    // end::publish-headers[]
    // tag::publish-expiration[]
    channel.basicPublish(
        exchangeName,
        routingKey,
        new AMQP.BasicProperties.Builder().expiration("60000").build(),
        messageBodyBytes);
    // end::publish-expiration[]
  }

  void defaultConsumer() throws Exception {
    Channel channel = null;
    String queueName = null;
    // tag::default-consumer[]
    boolean autoAck = false;
    channel.basicConsume(
        queueName,
        autoAck,
        "myConsumerTag",
        new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(
              String consumerTag, Envelope envelope,
              AMQP.BasicProperties properties, byte[] body)
              throws IOException {
            String routingKey = envelope.getRoutingKey();
            String contentType = properties.getContentType();
            long deliveryTag = envelope.getDeliveryTag();
            // (process the message components here ...)
            channel.basicAck(deliveryTag, false);
          }
        });
    // end::default-consumer[]
  }

  void consumerCancel() throws Exception {
    Channel channel = null;
    String consumerTag = null;
    // tag::consumer-cancel[]
    channel.basicCancel(consumerTag);
    // end::consumer-cancel[]
  }

  void basicGet() throws Exception {
    Channel channel = null;
    String queueName = null;
    // tag::basic-get[]
    boolean autoAck = false;
    GetResponse response = channel.basicGet(queueName, autoAck);
    if (response == null) {
      // No message retrieved.
    } else {
      AMQP.BasicProperties props = response.getProps();
      byte[] body = response.getBody();
      long deliveryTag = response.getEnvelope().getDeliveryTag();
      // ...
    }
    // end::basic-get[]
    // tag::basic-get-ack[]
    channel.basicAck(response.getEnvelope().getDeliveryTag(), false);  // <1>
    // end::basic-get-ack[]
  }

  void returning() {
    Channel channel = null;
    // tag::returning[]
    channel.addReturnListener((replyCode, replyText, exchange,
                               routingKey, properties, body) -> {
      // handle unroutable message
    });
    // end::returning[]
  }

  void shutdownListener() {
    Connection connection = null;
    // tag::shutdown-listener[]
    connection.addShutdownListener(cause -> {
      // action to run after shutdown, e.g. logging
    });
    // end::shutdown-listener[]
  }

  void shutdownCause() {
    Connection connection = null;
    // tag::shutdown-cause[]
    connection.addShutdownListener(cause -> {
      if (cause.isHardError()) {
        Connection conn = (Connection) cause.getReference();
        if (!cause.isInitiatedByApplication()) {
          Method reason = cause.getReason();
          // ...
        }
      } else {
        Channel ch = (Channel) cause.getReference();
        // ...
      }
    });
    // end::shutdown-cause[]
  }

  void shutdownAtomicity() throws Exception {
    Channel channel = null;
    // tag::shutdown-atomicity-broken[]
    // broken code, do not do this
    if (channel.isOpen()) {
      // The following code depends on the channel being in open state.
      // However, there is a possibility of the change in the channel state
      // between isOpen() and basicQos(1) call
      // ...
      channel.basicQos(1);
    }
    // end::shutdown-atomicity-broken[]

    // tag::shutdown-atomicity-valid[]
    try {
      // ...
      channel.basicQos(1);
    } catch (ShutdownSignalException sse) {
      // possibly check if channel was closed
      // by the time we started action and reasons for
      // closing it
      // ...
    } catch (IOException ioe) {
      // check why connection was closed
      // ...
    }
    // end::shutdown-atomicity-valid[]
  }

  void consumerThreadPool() throws Exception {
    ConnectionFactory factory = null;
    // tag::consumer-thread-pool[]
    ExecutorService es = Executors.newFixedThreadPool(20);
    Connection conn = factory.newConnection(es);
    // end::consumer-thread-pool[]
  }

  void addressResolver() throws Exception {
    ConnectionFactory factory = null;
    com.rabbitmq.client.AddressResolver addressResolver = null;
    // tag::address-resolver[]
    Connection conn = factory.newConnection(addressResolver);
    // end::address-resolver[]
  }

  // tag::address-resolver-interface[]
  public interface AddressResolver {

    List<Address> getAddresses() throws IOException;

  }
  // end::address-resolver-interface[]

  void nio() throws Exception {
    ConnectionFactory factory = null;
    // tag::nio-on[]
    ConnectionFactory connectionFactory = new ConnectionFactory();
    connectionFactory.useNio();
    // end::nio-on[]
    // tag::nio-params[]
    connectionFactory.setNioParams(new NioParams().setNbIoThreads(4));
    // end::nio-params[]
  }

  void recoveryEnable() throws Exception {
    String username = null, password = null, virtualHost = null, host = null;
    int port = 5672;
    // tag::recovery-enable[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setVirtualHost(virtualHost);
    factory.setHost(host);
    factory.setPort(port);
    factory.setAutomaticRecoveryEnabled(true);  // <1>
    Connection conn = factory.newConnection();
    // end::recovery-enable[]
  }

  void recoveryInterval() {
    // tag::recovery-interval[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setNetworkRecoveryInterval(10_000);  // <1>
    // end::recovery-interval[]
  }

  void recoveryEndpointList() throws Exception {
    // tag::recovery-endpoint-list[]
    ConnectionFactory factory = new ConnectionFactory();
    Address[] addresses = {new Address("192.168.1.4"),
                           new Address("192.168.1.5")};
    factory.newConnection(addresses);
    // end::recovery-endpoint-list[]
  }

  void recoveryRetryInitialConnection() throws Exception {
    // tag::recovery-retry-initial-connection[]
    ConnectionFactory factory = new ConnectionFactory();
    // configure various connection settings
    try {
      Connection conn = factory.newConnection();
    } catch (java.net.ConnectException e) {
      Thread.sleep(5000);
      // apply retry logic
    }
    // end::recovery-retry-initial-connection[]
  }

  void topologyRecoveryEnable() throws Exception {
    // tag::topology-recovery-enable[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setAutomaticRecoveryEnabled(true);  // <1>
    factory.setTopologyRecoveryEnabled(false);  // <2>
    Connection conn = factory.newConnection();
    // end::topology-recovery-enable[]
  }

  void exceptionHandler() throws Exception {
    ExceptionHandler customHandler = null;
    // tag::exception-handler[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setExceptionHandler(customHandler);
    // end::exception-handler[]
  }

  void metricsPrometheus() {
    // tag::metrics-prometheus[]
    ConnectionFactory factory = new ConnectionFactory();
    MeterRegistry registry = new PrometheusMeterRegistry(  // <1>
        PrometheusConfig.DEFAULT
    );
    MetricsCollector metricsCollector = new MicrometerMetricsCollector(  // <2>
        registry
    );
    factory.setMetricsCollector(metricsCollector);  // <3>
    // end::metrics-prometheus[]
  }

  void metricsDropwizard() {
    // tag::metrics-dropwizard[]
    ConnectionFactory connectionFactory = new ConnectionFactory();
    StandardMetricsCollector metricsCollector =
        new StandardMetricsCollector();
    connectionFactory.setMetricsCollector(metricsCollector);
    // ...
    metricsCollector.getPublishedMessages();  // <1>
    // end::metrics-dropwizard[]
  }

  void metricsDropwizardJmx() {
    // tag::metrics-dropwizard-jmx[]
    MetricRegistry registry = new MetricRegistry();
    MetricsCollector metricsCollector = new StandardMetricsCollector(
        registry
    );

    ConnectionFactory connectionFactory = new ConnectionFactory();
    connectionFactory.setMetricsCollector(metricsCollector);

    JmxReporter reporter = JmxReporter.forRegistry(registry)
        .inDomain("com.rabbitmq.client.jmx")
        .build();
    reporter.start();
    // end::metrics-dropwizard-jmx[]
  }

  void googleAppEngineHeartbeart() {
    // tag::gae-heartbeat[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setRequestedHeartbeat(5);
    // end::gae-heartbeat[]
  }

  void rpc() throws Exception {
    Channel channel = null;
    String exchangeName = null;
    String routingKey = null;
    // tag::rpc-client[]
    RpcClient rpc = new RpcClient(new RpcClientParams()
        .channel(channel)
        .exchange(exchangeName)
        .routingKey(routingKey));
    // end::rpc-client[]
  }

  void tlsSimple() throws Exception {
    // tag::tls-simple[]
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    factory.setPort(5671);
    // Only suitable for development.
    // This code will not perform peer certificate chain verification
    // and is prone to man-in-the-middle attacks.
    // See the main TLS guide to learn about peer verification
    // and how to enable it.
    factory.useSslProtocol();
    // end::tls-simple[]
  }

  void oauth2() {
    ConnectionFactory connectionFactory = new ConnectionFactory();
    // tag::oauth2-credentials[]
    // from com.rabbitmq.client.impl package
    CredentialsProvider credentialsProvider =
        new OAuth2ClientCredentialsGrantCredentialsProviderBuilder()
            .tokenEndpointUri("http://localhost:8080/uaa/oauth/token/")
            .clientId("rabbit_client").clientSecret("rabbit_secret")
            .grantType("password")
            .parameter("username", "rabbit_super")
            .parameter("password", "rabbit_super")
            .build();

    connectionFactory.setCredentialsProvider(credentialsProvider);
    // end::oauth2-credentials[]
  }

  void oauth2Tls() throws Exception {
    ConnectionFactory connectionFactory = new ConnectionFactory();
    // tag::oauth2-credentials-tls[]
    SSLContext sslContext = SSLContext.getInstance("TLSv1.3"); // <1>

    CredentialsProvider credentialsProvider =
        new OAuth2ClientCredentialsGrantCredentialsProviderBuilder()
            .tokenEndpointUri("https://localhost:8443/uaa/oauth/token/")
            .clientId("rabbit_client")
            .clientSecret("rabbit_secret")
            .grantType("password")
            .parameter("username", "rabbit_super")
            .parameter("password", "rabbit_super")
            .tls() // <2>
                .sslContext(sslContext) // <3>
                .builder() // <4>
            .build();
    // end::oauth2-credentials-tls[]
  }

  void oauth2Refresh() throws Exception {
    ConnectionFactory connectionFactory = new ConnectionFactory();
    // tag::oauth2-refresh[]
    // from com.rabbitmq.client.impl
    CredentialsRefreshService refreshService =
        new DefaultCredentialsRefreshServiceBuilder().build();
    connectionFactory.setCredentialsRefreshService(refreshService);
    // end::oauth2-refresh[]
  }
}
