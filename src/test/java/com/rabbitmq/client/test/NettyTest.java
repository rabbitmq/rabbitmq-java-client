package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static com.rabbitmq.client.test.TestUtils.LatchConditions.completed;
import static org.assertj.core.api.Assertions.assertThat;

public class NettyTest {

  ConnectionFactory cf;

  @BeforeEach
  void init() {
    cf = TestUtils.connectionFactory();
  }

  @Test
  void test() throws Exception {
    try (Connection c = cf.newConnection()) {
      Channel ch1 = c.createChannel();
      String q = ch1.queueDeclare().getQueue();

      Channel ch2 = c.createChannel();
      CountDownLatch consumeLatch = new CountDownLatch(1);
      CountDownLatch cancelLatch = new CountDownLatch(1);
      String ctag  = ch2.basicConsume(q, new DefaultConsumer(ch2) {
        @Override
        public void handleDelivery(String ctg, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          ch2.basicAck(envelope.getDeliveryTag(), false);
          consumeLatch.countDown();
        }

        @Override
        public void handleCancelOk(String consumerTag) {
          cancelLatch.countDown();
        }
      });

      Channel ch3 = c.createChannel();
      ch3.confirmSelect();
      CountDownLatch confirmLatch = new CountDownLatch(1);
      ch3.addConfirmListener((deliveryTag, multiple) -> confirmLatch.countDown(), (dtag, multiple) -> {});
      ch3.basicPublish("", q, null, "hello".getBytes(StandardCharsets.UTF_8));
      assertThat(confirmLatch).is(completed());
      assertThat(consumeLatch).is(completed());

      TestUtils.waitAtMost(() -> ch1.queueDeclarePassive(q).getMessageCount() == 0);

      ch2.basicCancel(ctag);
      assertThat(cancelLatch).is(completed());

      ch1.queueDelete(q);

      ch3.close();
      ch2.close();
      ch1.close();
    }
  }

}
