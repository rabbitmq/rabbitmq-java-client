package com.rabbitmq.client.test.server;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.client.impl.ConsumerWorkService;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

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
            super(factory.params(Executors.newFixedThreadPool(1)), new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", AMQP.PROTOCOL.PORT)));
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

    public void testChannelMaxLowerThanServerMinimum() throws Exception {
        int n = 64;
        ConnectionFactory cf = new ConnectionFactory();
        cf.setRequestedChannelMax(n);

        Connection conn = cf.newConnection();
        assertEquals(n, conn.getChannelMax());
    }

    public void testChannelMaxGreaterThanServerValue() throws Exception {
        try {
            Host.rabbitmqctl("eval 'application:set_env(rabbit, channel_max, 2048).'");

            SpecialConnection connection = new SpecialConnection(4096);
            try {
                connection.start();
                fail("expected failure during connection negotiation");
            } catch (IOException e) {
                // expected
            }
        } finally {
            Host.rabbitmqctl("eval 'application:set_env(rabbit, channel_max, 0).'");
        }
    }

    public void testOpeningTooManyChannels() throws Exception {
        int n = 48;

        try {
            Host.rabbitmqctl("eval 'application:set_env(rabbit, channel_max, " + n + ").'");
            ConnectionFactory cf = new ConnectionFactory();
            Connection conn = cf.newConnection();
            assertEquals(n, conn.getChannelMax());

            for (int i = 1; i <= n; i++) {
                assertNotNull(conn.createChannel(i));
            }
            // ChannelManager guards against channel.open being sent
            assertNull(conn.createChannel(n + 1));

            // Construct a channel directly
            final ChannelN ch = new ChannelN((AMQConnection) conn, n + 1,
                                             new ConsumerWorkService(Executors.newSingleThreadExecutor(),
                                                     Executors.defaultThreadFactory(), ConnectionFactory.DEFAULT_SHUTDOWN_TIMEOUT));
            conn.addShutdownListener(new ShutdownListener() {
                public void shutdownCompleted(ShutdownSignalException cause) {
                    // make sure channel.open continuation is released
                    ch.processShutdownSignal(cause, true, true);
                }
            });
            ch.open();
            fail("expected channel.open to cause a connection exception");
        } catch (IOException e) {
            checkShutdownSignal(530, e);
        } finally {
            Host.rabbitmqctl("eval 'application:set_env(rabbit, channel_max, 0).'");
        }
    }
}
