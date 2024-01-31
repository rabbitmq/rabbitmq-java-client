package com.rabbitmq.docs;

// tag::imports[]
import com.rabbitmq.client.*;
// end::imports[]

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
    factory.setUri("amqp://userName:password@hostName:portNumber/virtualHost");
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
}
