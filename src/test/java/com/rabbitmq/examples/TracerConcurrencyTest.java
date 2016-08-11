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

package com.rabbitmq.examples;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Java Application. Repeatedly generate (and get) messages on multiple concurrently processing channels.
 * <p/>
 * This application connects to localhost port 5673, and is useful for testing {@link com.rabbitmq.tools.Tracer Tracer}.
 * @see com.rabbitmq.tools.Tracer Tracer
 */
public class TracerConcurrencyTest {

    private static final String uri = "amqp://localhost:5673";
    private static final int THREADCOUNT = 3;
    private static final String EXCHANGE = "tracer-exchange";
    private static final String QUEUE = "tracer-queue";
    private static final String ROUTING_KEY = "";

    /**
     * @param args command-line parameters -- all ignored.
     * @throws Exception test
     */
    public static void main(String[] args) throws Exception {

        final Object outputSync = new Object();

        final Connection conn = createConnectionAndResources();

        for (int i = 0; i < THREADCOUNT; i++) {
            new TestThread(conn, outputSync).start();
        }
    }

    private static class TestThread extends Thread {
        private final Connection conn;
        private final Object outputSync;

        private TestThread(Connection conn, Object outputSync) {
            this.conn = conn;
            this.outputSync = outputSync;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Channel ch = conn.createChannel();
                    ch.basicPublish(EXCHANGE, ROUTING_KEY, null, new byte[1024 * 1024]);
                    ch.basicGet(QUEUE, true);
                    ch.close();
                }
            } catch (Exception e) {
                synchronized (outputSync) {
                    e.printStackTrace();
                    System.err.println();
                }
                System.exit(1);
            }
        }

    }

    /**
     * Create connection and declare exchange and queue for local use.
     *
     * @return connection
     */
    private static Connection createConnectionAndResources() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setUri(uri);
        Connection conn = cf.newConnection();
        Channel setup = conn.createChannel();

        setup.exchangeDeclare(EXCHANGE, "direct");
        setup.queueDeclare(QUEUE, false, false, false, null);
        setup.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);

        setup.close();
        return conn;
    }
}
