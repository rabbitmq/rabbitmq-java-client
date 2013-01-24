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

package com.rabbitmq.client.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ExceptionHandler;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;

/**
 * Test suite for AMQConnection.
 */

public class AMQConnectionTest extends TestCase {
    // private static final String CLOSE_MESSAGE = "terminated by test";

    /**
     * Build a suite of tests
     * @return the test suite for this class
     */
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("connection");
        suite.addTestSuite(AMQConnectionTest.class);
        return suite;
    }

    /** The mock frame handler used to test connection behaviour. */
    private MockFrameHandler _mockFrameHandler;
    private ConnectionFactory factory;

    /** Setup the environment for this test
     * @see junit.framework.TestCase#setUp()
     * @throws Exception if anything goes wrong
     */
    @Override protected void setUp() throws Exception {
        super.setUp();
        _mockFrameHandler = new MockFrameHandler();
        factory = new ConnectionFactory();
    }

    /** Tear down the environment for this test
     * @see junit.framework.TestCase#tearDown()
     * @throws Exception if anything goes wrong
     */
    @Override protected void tearDown() throws Exception {
        factory = null;
        _mockFrameHandler = null;
        super.tearDown();
    }

    /** Check the AMQConnection does send exactly 1 initial header, and deal correctly with
     * the frame handler throwing an exception when we try to read data
     */
    public void testConnectionSendsSingleHeaderAndTimesOut() {
        IOException exception = new SocketTimeoutException();
        _mockFrameHandler.setExceptionOnReadingFrames(exception);
        MyExceptionHandler handler = new MyExceptionHandler();
        assertEquals(0, _mockFrameHandler.countHeadersSent());
        try {
            new AMQConnection(factory.getUsername(),
                    factory.getPassword(),
                    _mockFrameHandler,
                    Executors.newFixedThreadPool(1),
                    factory.getVirtualHost(),
                    factory.getClientProperties(),
                    factory.getRequestedFrameMax(),
                    factory.getRequestedChannelMax(),
                    factory.getRequestedHeartbeat(),
                    factory.getSaslConfig(),
                    handler).start();
            fail("Connection should have thrown exception");
        } catch(IOException signal) {
           // As expected
        }
        assertEquals(1, _mockFrameHandler.countHeadersSent());
        // _connection.close(0, CLOSE_MESSAGE);
        List<Throwable> exceptionList = handler.getHandledExceptions();
        assertEquals(Collections.<Throwable>singletonList(exception), exceptionList);
    }

    /** Check we can open a connection once, but not twice.
     * @throws IOException */
//    public void testCanOpenConnectionOnceOnly() throws IOException {
//        AMQConnection connection = new AMQConnection(_mockFrameHandler);
//        connection.open();
//        try {
//            connection.open();
//            fail("We shouldn't have been able to open this connection more than once.");
//        } catch(IOException ex) {
//            // as expected
//        }
//    }

    /**
     * Test that we catch timeout between connect and negotiation of the connection being finished.
     */
    public void testConnectionHangInNegotiation() {
        this._mockFrameHandler.setTimeoutCount(10); // to limit hang
        MyExceptionHandler handler = new MyExceptionHandler();
        assertEquals(0, this._mockFrameHandler.countHeadersSent());
        try {
            new AMQConnection(factory.getUsername(),
                    factory.getPassword(),
                    this._mockFrameHandler,
                    Executors.newFixedThreadPool(1),
                    factory.getVirtualHost(),
                    factory.getClientProperties(),
                    factory.getRequestedFrameMax(),
                    factory.getRequestedChannelMax(),
                    factory.getRequestedHeartbeat(),
                    factory.getSaslConfig(),
                    handler).start();
            fail("Connection should have thrown exception");
        } catch(IOException signal) {
           // As expected
        }
        assertEquals(1, this._mockFrameHandler.countHeadersSent());
        // _connection.close(0, CLOSE_MESSAGE);
        List<Throwable> exceptionList = handler.getHandledExceptions();
        assertEquals("Only one exception expected", 1, exceptionList.size());
        assertEquals("Wrong type of exception returned.", SocketTimeoutException.class, exceptionList.get(0).getClass());
    }

    /** Mock frame handler to facilitate testing. */
    private static class MockFrameHandler implements FrameHandler {
        /** How many times has sendHeader() been called? */
        private int _numHeadersSent;

        private int timeout;

        /** An optional exception for us to throw on reading frames */
        private IOException _exceptionOnReadingFrames;

        private int timeoutCount = 0;

        /** count how many headers we've sent
         * @return the number of sent headers
         */
        public int countHeadersSent() {
            return _numHeadersSent;
        }

        public void setExceptionOnReadingFrames(IOException exception) {
            _exceptionOnReadingFrames = exception;
        }

        public void setTimeoutCount(int timeoutCount) {
            this.timeoutCount = timeoutCount;
        }

        public Frame readFrame() throws IOException {
            if (_exceptionOnReadingFrames != null) {
                throw _exceptionOnReadingFrames;
            }
            if (this.timeoutCount > 0) {
                if (--this.timeoutCount == 0)
                    throw new IOException("Mock Framehandler: too many timeouts.");
            }
            return null; // simulate a socket timeout
        }

        public void sendHeader() throws IOException {
            _numHeadersSent++;
        }

        public void setTimeout(int timeoutMs) throws SocketException {
            this.timeout = timeoutMs;
        }

        public void writeFrame(Frame frame) throws IOException {
            // no need to implement this: don't bother writing the frame
        }

        public void close() {
            // nothing to do
        }

        public int getTimeout() throws SocketException {
            return this.timeout;
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
    }

    /** Exception handler to facilitate testing. */
    private class MyExceptionHandler implements ExceptionHandler {
        private List<Throwable> _handledExceptions = new ArrayList<Throwable>();

        public void handleUnexpectedConnectionDriverException(Connection conn, Throwable ex) {
            _handledExceptions.add(ex);
        }

        public void handleReturnListenerException(Channel ch, Throwable ex) {
            fail("handleReturnListenerException: " + ex);
        }

        public void handleFlowListenerException(Channel ch, Throwable ex) {
            fail("handleFlowListenerException: " + ex);
        }

        public void handleConfirmListenerException(Channel ch, Throwable ex) {
            fail("handleConfirmListenerException: " + ex);
        }

        public void handleConsumerException(Channel ch,
                                            Throwable ex,
                                            Consumer c,
                                            String consumerTag,
                                            String methodName)
        {
            fail("handleConsumerException " + consumerTag + " " + methodName + ": " + ex);
        }

        public List<Throwable> getHandledExceptions() {
            return _handledExceptions;
        }
    }
}
