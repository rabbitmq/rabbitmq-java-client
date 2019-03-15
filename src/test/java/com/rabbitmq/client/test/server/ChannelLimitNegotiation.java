// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;

import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

public class ChannelLimitNegotiation extends BrokerTestCase {

    class SpecialConnection extends AMQConnection {
        private final int channelMax;

        public SpecialConnection(int channelMax) throws Exception {
            this(TestUtils.connectionFactory(), channelMax);
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

    @Test public void channelMaxLowerThanServerMinimum() throws Exception {
        int n = 64;
        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setRequestedChannelMax(n);

        try (Connection conn = cf.newConnection()) {
            assertEquals(n, conn.getChannelMax());
        }
    }

    @Test public void channelMaxGreaterThanServerValue() throws Exception {
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

    @Test public void openingTooManyChannels() throws Exception {
        int n = 48;

        Connection conn = null;
        try {
            Host.rabbitmqctl("eval 'application:set_env(rabbit, channel_max, " + n + ").'");
            ConnectionFactory cf = TestUtils.connectionFactory();
            conn = cf.newConnection();
            assertEquals(n, conn.getChannelMax());

            for (int i = 1; i <= n; i++) {
                assertNotNull(conn.createChannel(i));
            }
            // ChannelManager guards against channel.open being sent
            assertNull(conn.createChannel(n + 1));

            // Construct a channel directly
            final ChannelN ch = new ChannelN(((AutorecoveringConnection) conn).getDelegate(), n + 1,
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
            TestUtils.abort(conn);
            Host.rabbitmqctl("eval 'application:set_env(rabbit, channel_max, 0).'");
        }
    }
}
