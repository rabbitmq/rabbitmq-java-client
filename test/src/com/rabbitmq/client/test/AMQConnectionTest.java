//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
/**
 * 
 */
package com.rabbitmq.client.test;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.RedirectException;
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
    private ConnectionParameters _params;

    /** Setup the environment for this test
     * @see junit.framework.TestCase#setUp()
     * @throws Exception if anything goes wrong
     */
    @Override protected void setUp() throws Exception {
        super.setUp();
        _mockFrameHandler = new MockFrameHandler();
        _params = new ConnectionParameters();
    }

    /** Tear down the environment for this test
     * @see junit.framework.TestCase#tearDown()
     * @throws Exception if anything goes wrong
     */
    @Override protected void tearDown() throws Exception {
        _params = null;
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
            new AMQConnection(_params, false, _mockFrameHandler, handler);
            fail("Connection should have thrown exception");
        } catch(IOException signal) {
           // As expected 
        } catch(RedirectException e) {
            fail("Unexpected redirect");
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

// add test that we time out if no initial Start command is received,
// setting a timeout and having the FrameHandler return null
    
     /** Mock frame handler to facilitate testing. */
    public static class MockFrameHandler implements FrameHandler {
        /** How many times has sendHeader() been called? */
        private int _numHeadersSent;
        
        /** An optional exception for us to throw on reading frames */
        private IOException _exceptionOnReadingFrames;

        /** count how many headers we've sent 
         * @return the number of sent headers
         */
        public int countHeadersSent() {
            return _numHeadersSent;
        }

        public void setExceptionOnReadingFrames(IOException exception) {
            _exceptionOnReadingFrames = exception;            
        }

        public Frame readFrame() throws IOException {
            if (_exceptionOnReadingFrames != null) {
                throw _exceptionOnReadingFrames;
            }
            return null;
            // throw new SocketTimeoutException(); // simulate a socket timeout
        }

        public void sendHeader() throws IOException {
            _numHeadersSent++;            
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

        public String getHost() {
            return "MockFrameHandler";
        }

        public int getPort() {
            return -1;
        }
    }

    /** Mock frame handler to facilitate testing. */
    public class MyExceptionHandler implements ExceptionHandler {
        private List<Throwable> _handledExceptions = new ArrayList<Throwable>();

        public void handleUnexpectedConnectionDriverException(Connection conn, Throwable ex) {
            _handledExceptions.add(ex);
        }

        public void handleReturnListenerException(Channel ch, Throwable ex) {
            fail("handleReturnListenerException: " + ex);
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
