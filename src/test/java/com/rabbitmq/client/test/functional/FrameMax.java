// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import static com.rabbitmq.client.test.TestUtils.LatchConditions.completed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.test.TestUtils;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.LongStringHelper;
import com.rabbitmq.client.test.BrokerTestCase;

public class FrameMax extends BrokerTestCase {
    /* This value for FrameMax is larger than the minimum and less
     * than what Rabbit suggests. */
    final static int FRAME_MAX = 70000;

    @Override
    protected ConnectionFactory newConnectionFactory() {
        ConnectionFactory cf = super.newConnectionFactory();
        cf.setRequestedFrameMax(FRAME_MAX);
        return cf;
    }

    @Test public void negotiationOk() {
        assertEquals(FRAME_MAX, connection.getFrameMax());
    }

    /* Publish a message of size FRAME_MAX.  The broker should split
     * this into two frames before sending back.  Frame content should
     * be less or equal to frame-max - 8. */
    @Test public void frameSizes() throws IOException {
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
                fail("Exception in basicGet loop: " + e);
            }
        }
    }

    /* server should reject frames larger than AMQP.FRAME_MIN_SIZE
     * during connection negotiation */
    @Test public void rejectLargeFramesDuringConnectionNegotiation() throws TimeoutException {
        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.getClientProperties().put("too_long", LongStringHelper.asLongString(new byte[AMQP.FRAME_MIN_SIZE]));
        try {
            cf.newConnection();
            fail("Expected exception during connection negotiation");
        } catch (IOException e) {
        }
    }

    /* server should reject frames larger than the negotiated frame
     * size */
    @Test public void rejectExceedingFrameMax() throws IOException, TimeoutException {
        closeChannel();
        closeConnection();
        ConnectionFactory cf = new GenerousConnectionFactory();
        cf.setRequestedFrameMax(8192);
        connection = cf.newConnection();
        openChannel();
        basicPublishVolatile(new byte[connection.getFrameMax() * 2], "void");
        expectError(AMQP.FRAME_ERROR);
    }

    /* client should throw exception if headers exceed negotiated
     * frame size */
    @Test public void rejectHeadersExceedingFrameMax() throws IOException {
        declareTransientTopicExchange("x");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, "x", "foobar");

        Map<String, Object> headers = new HashMap<String, Object>();
        String headerName = "x-huge-header";

        // create headers with zero-length value to calculate maximum header value size before exceeding frame_max
        headers.put(headerName, LongStringHelper.asLongString(new byte[0]));
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                                                  .headers(headers)
                                                  .build();
        Frame minimalHeaderFrame = properties.toFrame(0, 0);
        int maxHeaderValueSize = FRAME_MAX - minimalHeaderFrame.size();

        // create headers with maximum header value size (frame size equals frame_max)
        headers.put(headerName, LongStringHelper.asLongString(new byte[maxHeaderValueSize]));
        properties = new AMQP.BasicProperties.Builder().headers(headers).build();

        basicPublishVolatile(new byte[100], "x", "foobar", properties);
        assertDelivered(queueName, 1);

        // create headers with frame size exceeding frame_max by 1
        headers.put(headerName, LongStringHelper.asLongString(new byte[maxHeaderValueSize + 1]));
        properties = new AMQP.BasicProperties.Builder().headers(headers).build();
        try {
            basicPublishVolatile(new byte[100], "x", "foobar", properties);
            fail("expected rejectHeadersExceedingFrameMax to throw");
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().startsWith("Content headers exceeded max frame size"));
            // check that the channel is still operational
            assertDelivered(queueName, 0);
        }

        // cleanup
        deleteExchange("x");
    }


    // see rabbitmq/rabbitmq-java-client#407
    @Test public void unlimitedFrameMaxWithHeaders()
            throws IOException, TimeoutException {
        closeChannel();
        closeConnection();
        ConnectionFactory cf = newConnectionFactory();
        cf.setRequestedFrameMax(0);
        connection = cf.newConnection();
        openChannel();
        channel.confirmSelect();
        CountDownLatch confirmLatch = new CountDownLatch(1);
        channel.addConfirmListener((deliveryTag, multiple) -> confirmLatch.countDown(), (dtag, m) -> { });

        Map<String, Object> headers = new HashMap<>();
        headers.put("h1", LongStringHelper.asLongString(new byte[50000]));
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                                                  .headers(headers)
                                                  .build();
        basicPublishVolatile(new byte[500000], "", "", properties);
        assertThat(confirmLatch).is(completed());
    }

    @Override
    protected boolean isAutomaticRecoveryEnabled() {
        return false;
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

        public GenerousConnectionFactory() {
            super();
            TestUtils.setIoLayer(this);
        }

        @Override public Connection newConnection(ExecutorService executor, List<Address> addrs)
                throws IOException, TimeoutException {
            IOException lastException = null;
            for (Address addr : addrs) {
                try {
                    FrameHandler frameHandler = createFrameHandlerFactory().create(addr, null);
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
