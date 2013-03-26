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


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.LongStringHelper;
import com.rabbitmq.client.impl.SocketFrameHandler;

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
        throws IOException
    {
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
        throws IOException
    {
        closeChannel();
        closeConnection();
        ConnectionFactory cf = new GenerousConnectionFactory();
        connection = cf.newConnection();
        openChannel();
        try {
            basicPublishVolatile(new byte[connection.getFrameMax()], "void");
            channel.basicQos(0);
            fail("Expected exception when publishing");
        } catch (IOException e) {
        }
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
            super(factory.getUsername(),
                  factory.getPassword(),
                  handler,
                  executor,
                  factory.getVirtualHost(),
                  factory.getClientProperties(),
                  factory.getRequestedFrameMax(),
                  factory.getRequestedChannelMax(),
                  factory.getRequestedHeartbeat(),
                  factory.getSaslConfig());
        }

        @Override public int getFrameMax() {
            // the RabbitMQ broker permits frames that are oversize by
            // up to EMPTY_FRAME_SIZE octets
            return super.getFrameMax() + AMQCommand.EMPTY_FRAME_SIZE + 1;
        }

    }

    private static class GenerousConnectionFactory extends ConnectionFactory {

        @Override public Connection newConnection(ExecutorService executor, Address[] addrs)
            throws IOException
        {
            IOException lastException = null;
            for (Address addr : addrs) {
                try {
                    FrameHandler frameHandler = createFrameHandler(addr);
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
