package com.rabbitmq.client.test.server;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.client.test.BrokerTestCase;

import javax.net.SocketFactory;
import java.io.IOException;
import java.util.concurrent.Executors;

public class ChannelLimitNegotiation extends BrokerTestCase {
  class SpecialConnection extends AMQConnection {
    private final int channelMax;

    public SpecialConnection(int channelMax) throws Exception {
      this(new ConnectionFactory(), channelMax);
    }

    private SpecialConnection(ConnectionFactory factory, int channelMax) throws Exception {
      super(factory.getUsername(),
          factory.getPassword(),
          new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", AMQP.PROTOCOL.PORT)),
          Executors.newFixedThreadPool(1),
          factory.getVirtualHost(),
          factory.getClientProperties(),
          factory.getRequestedFrameMax(),
          channelMax,
          factory.getRequestedHeartbeat(),
          factory.getSaslConfig(),
          null);

      this.channelMax = channelMax;
    }

    /**
     * Private API, allows for easier simulation of bogus clients.
     */
    @Override
    protected int negotiateChannelMax(int requestedChannelMax, int serverMax) {
      return this.channelMax;
    }
  }

  public void testChannelMaxLowerThanServerValue() throws Exception {
    int n = 64;
    ConnectionFactory cf = new ConnectionFactory();
    cf.setRequestedChannelMax(n);

    Connection conn = cf.newConnection();
    assertEquals(n, conn.getChannelMax());
  }

  public void testChannelMaxGreaterThanServerValue() throws Exception {
      try {
        stopApp();
        startAppWithConfig("./../rabbitmq-java-client/test/etc/rabbitmq/with_channel_limit");

        boolean failed = false;
        SpecialConnection connection = new SpecialConnection(4096);
        try {
          connection.start();
        } catch (IOException e) {
          failed = true;
        }
      } finally {
        stopApp();
        startApp();
      }
  }
}
