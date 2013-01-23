//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

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
    private static final Connection createConnectionAndResources() throws Exception {
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
