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


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

public class FrameMax extends BrokerTestCase {
    /* This value for FrameMax is larger than the minimum and less
     * than what Rabbit suggests. */
    final static int FRAME_MAX = 70000;
    final static int REAL_FRAME_MAX = FRAME_MAX - 8;

    public FrameMax() {
        connectionFactory = new MyConnectionFactory();
        connectionFactory.setRequestedFrameMax(FRAME_MAX);
    }

    /* Publish a message of size FRAME_MAX.  The broker should split
     * this into two frames before sending back.  Frame content should
     * be less or equal to frame-max - 8. */
    public void testFrameSizes()
        throws IOException, InterruptedException
    {
        String queueName = channel.queueDeclare().getQueue();
        /* This should result in at least 3 frames. */
        int howMuch = 2*FRAME_MAX;
        basicPublishVolatile(new byte[howMuch], queueName);
        /* Receive everything that was sent out. */
        while (howMuch > 0) {
            try {
                GetResponse response = channel.basicGet(queueName, false);
                howMuch -= response.getBody().length;
            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception in basicGet loop: " + e);
            }
        }
    }

    /* server should reject frames larger than AMQP.FRAME_MIN_SIZE
     * during connection negotiation */
    public void testRejectLargeFramesDuringConnectionNegotiation()
            throws IOException, TimeoutException {
        ConnectionFactory cf = new ConnectionFactory();
        cf.getClientProperties().put("too_long", LongStringHelper.asLongString(new byte[AMQP.FRAME_MIN_SIZE]));
        try {
            cf.newConnection();
            fail("Expected exception during connection negotiation");
        } catch (IOException e) {
        }
    }

    /* server should reject frames larger than the negotiated frame
     * size */
    public void testRejectExceedingFrameMax()
            throws IOException, TimeoutException {
        closeChannel();
        closeConnection();
        ConnectionFactory cf = new GenerousConnectionFactory();
        connection = cf.newConnection();
        openChannel();
        basicPublishVolatile(new byte[connection.getFrameMax()], "void");
        expectError(AMQP.FRAME_ERROR);
    }

    /* ConnectionFactory that uses MyFrameHandler rather than
     * SocketFrameHandler. */
    private static class MyConnectionFactory extends ConnectionFactory {
        protected FrameHandler createFrameHandler(Socket sock)
            throws IOException
        {
            return new MyFrameHandler(sock);
        }
    }

    /* FrameHandler with added frame-max error checking. */
    private static class MyFrameHandler extends SocketFrameHandler {
        public MyFrameHandler(Socket socket)
            throws IOException
        {
            super(socket);
        }

        public Frame readFrame() throws IOException {
            Frame f = super.readFrame();
            int size = f.getPayload().length;
            if (size > REAL_FRAME_MAX)
                fail("Received frame of size " + size
                     + ", which exceeds " + REAL_FRAME_MAX + ".");
            //System.out.printf("Received a frame of size %d.\n", f.getPayload().length);
            return f;
        }
    }

    /*
      AMQConnection with a frame_max that is one higher than what it
      tells the server.
    */
    private static class GenerousAMQConnection extends AMQConnection {

        public GenerousAMQConnection(ConnectionFactory factory,
                                     FrameHandler      handler,
                                     ExecutorService   executor) {
            super(factory.params(executor), handler);
        }

        @Override public int getFrameMax() {
            // the RabbitMQ broker permits frames that are oversize by
            // up to EMPTY_FRAME_SIZE octets
            return super.getFrameMax() + AMQCommand.EMPTY_FRAME_SIZE + 1;
        }

    }

    private static class GenerousConnectionFactory extends ConnectionFactory {

        @Override public Connection newConnection(ExecutorService executor, List<Address> addrs)
                throws IOException, TimeoutException {
            IOException lastException = null;
            for (Address addr : addrs) {
                try {
                    FrameHandler frameHandler = createFrameHandlerFactory().create(addr);
                    AMQConnection conn = new GenerousAMQConnection(this, frameHandler, executor);
                    conn.start();
                    return conn;
                } catch (IOException e) {
                    lastException = e;
                }
            }
            throw (lastException != null) ? lastException
                : new IOException("failed to connect");
        }
    }

}
