///usr/bin/env jbang "$0" "$@" ; exit $?
//REPOS mavencentral,ossrh-staging=https://oss.sonatype.org/content/groups/staging/,rabbitmq-packagecloud-milestones=https://packagecloud.io/rabbitmq/maven-milestones/maven2
//DEPS com.rabbitmq:amqp-client:${version}
//DEPS org.slf4j:slf4j-simple:1.7.36

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.ClientVersion;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SanityCheck {

  private static final Logger LOGGER = LoggerFactory.getLogger("rabbitmq");

  public static void main(String[] args) {
    try (Connection connection = new ConnectionFactory().newConnection()) {
      Channel ch = connection.createChannel();
      String queue = ch.queueDeclare().getQueue();
      CountDownLatch latch = new CountDownLatch(1);
      ch.basicConsume(
          queue,
          true,
          new DefaultConsumer(ch) {
            @Override
            public void handleDelivery(
                String consumerTag,
                Envelope envelope,
                AMQP.BasicProperties properties,
                byte[] body) {
              latch.countDown();
            }
          });
      ch.basicPublish("", queue, null, "test".getBytes());
      boolean received = latch.await(5, TimeUnit.SECONDS);
      if (!received) {
        throw new IllegalStateException("Didn't receive message in 5 seconds");
      }
      LOGGER.info("Test succeeded with Java client {}", ClientVersion.VERSION);
      System.exit(0);
    } catch (Exception e) {
      LOGGER.info("Test failed  with Java client {}", ClientVersion.VERSION, e);
      System.exit(1);
    }
  }
}
