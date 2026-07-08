// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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


package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.UnexpectedFrameError;
import com.rabbitmq.client.WriteListener;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.AMQImpl.Basic.Publish;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class BrokenFramesTest {

    private MyFrameHandler myFrameHandler;
    private ConnectionFactory factory;

    @BeforeEach public void setUp() {
        myFrameHandler = new MyFrameHandler();
        factory = TestUtils.connectionFactory();
    }

    @AfterEach public void tearDown() {
        factory = null;
        myFrameHandler = null;
    }

    @Test public void noMethod() throws Exception {
        List<Frame> frames = new ArrayList<Frame>();
        frames.add(new Frame(AMQP.FRAME_HEADER, 0));
        myFrameHandler.setFrames(frames.iterator());

        try {
            new AMQConnection(factory.params(Executors.newFixedThreadPool(1)), myFrameHandler).start();
        } catch (IOException e) {
            UnexpectedFrameError unexpectedFrameError = findUnexpectedFrameError(e);
            assertNotNull(unexpectedFrameError);
            assertEquals(AMQP.FRAME_HEADER, unexpectedFrameError.getReceivedFrame().getType());
            assertEquals(AMQP.FRAME_METHOD, unexpectedFrameError.getExpectedFrameType());
            return;
        }

        fail("No UnexpectedFrameError thrown");
    }

    @Test public void methodThenBody() throws Exception {
        List<Frame> frames = new ArrayList<Frame>();

        byte[] contentBody = new byte[10];
        int channelNumber = 0;

        Publish method = new Publish(1, "test", "test", false, false);

        frames.add(method.toFrame(0));
        frames.add(Frame.fromBodyFragment(channelNumber, ByteBuffer.wrap(contentBody), 0, contentBody.length));

        myFrameHandler.setFrames(frames.iterator());

        try {
            new AMQConnection(factory.params(Executors.newFixedThreadPool(1)), myFrameHandler).start();
        } catch (IOException e) {
            UnexpectedFrameError unexpectedFrameError = findUnexpectedFrameError(e);
            assertNotNull(unexpectedFrameError);
            assertEquals(AMQP.FRAME_BODY, unexpectedFrameError.getReceivedFrame().getType());
            assertEquals(AMQP.FRAME_HEADER, unexpectedFrameError.getExpectedFrameType());
            return;
        }

        fail("No UnexpectedFrameError thrown");
    }

    @Test
    public void bodyOverrunTriggersConnectionClosure() throws Exception {
        List<Frame> frames = new ArrayList<>();
        int channelNumber = 0;
        factory.setMaxInboundMessageBodySize(1024);
        AtomicReference<Throwable> exception = new AtomicReference<>();
        factory.setExceptionHandler(
            new DefaultExceptionHandler() {
                @Override
                public void handleUnexpectedConnectionDriverException(
                    Connection conn, Throwable ex) {
                    exception.set(ex);
                }
            });

        // 1. Setup a valid method frame (Basic.Deliver expects content)
        AMQImpl.Basic.Deliver method = new AMQImpl.Basic.Deliver("ctag", 1L, false, "", "rk");
        frames.add(method.toFrame(channelNumber));

        // 2. Declares exactly 1 byte of body size
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        frames.add(props.toFrame(channelNumber, 1L));

        // 3. Payload is 2 bytes (0x41, 0x42), exceeding the declared size
        frames.add(new Frame(AMQP.FRAME_BODY, channelNumber, new byte[] { 0x41, 0x42 }));

        myFrameHandler.setFrames(frames.iterator());

        AMQConnection connection = null;
        try {
            connection = new AMQConnection(factory.params(Executors.newFixedThreadPool(1)), myFrameHandler);
            connection.start();
            fail("Expected an exception to be thrown due to body overrun, but none was.");
        } catch (IOException e) {
            assertThat(e).hasRootCauseInstanceOf(MalformedFrameException.class);
            assertThat(exception.get()).isNotNull().isInstanceOf(MalformedFrameException.class);
        } finally {
            assertTrue(myFrameHandler.closeCalled, "The underlying FrameHandler (socket) should have been closed.");
            if (connection != null) {
                assertFalse(connection.isOpen(), "The AMQConnection should be marked as closed.");
            }
        }
    }

    @Test
    public void negativeBodySizeTriggersConnectionClosure() throws Exception {
        List<Frame> frames = new ArrayList<>();
        int channelNumber = 0;
        AtomicReference<Throwable> exception = new AtomicReference<>();
        factory.setExceptionHandler(
            new DefaultExceptionHandler() {
                @Override
                public void handleUnexpectedConnectionDriverException(
                    Connection conn, Throwable ex) {
                    exception.set(ex);
                }
            });

        // 1. Setup a valid method frame (Basic.Deliver expects content)
        AMQImpl.Basic.Deliver method = new AMQImpl.Basic.Deliver("ctag", 1L, false, "", "rk");
        frames.add(method.toFrame(channelNumber));

        // 2. Forge a content header frame with a negative body size (class id 60 = basic,
        // weight 0, body size -1, no property flags)
        ByteArrayOutputStream headerPayload = new ByteArrayOutputStream();
        DataOutputStream headerOut = new DataOutputStream(headerPayload);
        headerOut.writeShort(60); // basic class id
        headerOut.writeShort(0);  // weight
        headerOut.writeLong(-1L); // forged negative body size
        headerOut.writeShort(0);  // no properties
        frames.add(new Frame(AMQP.FRAME_HEADER, channelNumber, headerPayload.toByteArray()));

        myFrameHandler.setFrames(frames.iterator());

        AMQConnection connection = null;
        try {
            connection = new AMQConnection(factory.params(Executors.newFixedThreadPool(1)), myFrameHandler);
            connection.start();
            fail("Expected an exception to be thrown due to negative body size, but none was.");
        } catch (IOException e) {
            assertThat(e).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(exception.get()).isNotNull().isInstanceOf(IllegalStateException.class);
        } finally {
            assertTrue(myFrameHandler.closeCalled, "The underlying FrameHandler (socket) should have been closed.");
            if (connection != null) {
                assertFalse(connection.isOpen(), "The AMQConnection should be marked as closed.");
            }
        }
    }

    private UnexpectedFrameError findUnexpectedFrameError(Exception e) {
        Throwable t = e;
        while ((t = t.getCause()) != null) {
            if (t instanceof UnexpectedFrameError) {
                // This is what we wanted
                return (UnexpectedFrameError) t;
            }
        }
        return null;
    }

    private static class MyFrameHandler implements FrameHandler {
        private Iterator<Frame> frames;
        public boolean closeCalled = false;

        public void setFrames(Iterator<Frame> frames) {
            this.frames = frames;
        }

        public Frame readFrame() {
            return frames.next();
        }

        public void sendHeader() {
        }

        @Override
        public void initialize(AMQConnection connection) {
            connection.startMainLoop();
        }

        public void setTimeout(int timeoutMs) {
            // no need to implement this: don't bother changing the timeout
        }

        public void writeFrame(Frame frame) {
            // no need to implement this: don't bother writing the frame
        }

        public void close() {
            this.closeCalled = true;
        }

        public int getTimeout() {
            return 0;
        }

        public InetAddress getAddress() {
            return null;
        }

        public int getPort() {
            return -1;
        }

        public void flush(WriteListener listener) {
            // no need to implement this: don't bother writing the frame
        }

        public InetAddress getLocalAddress() {
            return null;
        }

        public int getLocalPort() {
            return -1;
        }
    }

}
