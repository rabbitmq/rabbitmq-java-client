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


package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.UnexpectedFrameError;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.AMQImpl.Basic.Publish;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class BrokenFramesTest {

    private MyFrameHandler myFrameHandler;
    private ConnectionFactory factory;

    @Before public void setUp() {
        myFrameHandler = new MyFrameHandler();
        factory = TestUtils.connectionFactory();
    }

    @After public void tearDown() {
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
        frames.add(Frame.fromBodyFragment(channelNumber, contentBody, 0, contentBody.length));

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

        public void setFrames(Iterator<Frame> frames) {
            this.frames = frames;
        }

        public Frame readFrame() throws IOException {
            return frames.next();
        }

        public void sendHeader() throws IOException {
        }

        @Override
        public void initialize(AMQConnection connection) {
            connection.startMainLoop();
        }

        public void setTimeout(int timeoutMs) throws SocketException {
            // no need to implement this: don't bother changing the timeout
        }

        public void writeFrame(Frame frame) throws IOException {
            // no need to implement this: don't bother writing the frame
        }

        public void close() {
            // nothing to do
        }

        public int getTimeout() throws SocketException {
            return 0;
        }

        public InetAddress getAddress() {
            return null;
        }

        public int getPort() {
            return -1;
        }

        public void flush() throws IOException {
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
