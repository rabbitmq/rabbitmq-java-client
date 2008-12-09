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

package com.rabbitmq.client.test;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.UnexpectedFrameError;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.AMQImpl.Basic.Publish;

public class BrokenFramesTest extends TestCase {
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("connection");
        suite.addTestSuite(BrokenFramesTest.class);
        return suite;
    }

    private MyFrameHandler myFrameHandler;
    private ConnectionParameters params;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFrameHandler = new MyFrameHandler();
        params = new ConnectionParameters();
    }

    @Override
    protected void tearDown() throws Exception {
        params = null;
        myFrameHandler = null;
        super.tearDown();
    }

    public void testNoMethod() throws Exception {
        List<Frame> frames = new ArrayList<Frame>();
        frames.add(new Frame(AMQP.FRAME_HEADER, 0));
        myFrameHandler.setFrames(frames.iterator());

        try {
            new AMQConnection(params, false, myFrameHandler);
        } catch (IOException e) {
            UnexpectedFrameError unexpectedFrameError = findUnexpectedFrameError(e);
            assertNotNull(unexpectedFrameError);
            assertEquals(AMQP.FRAME_HEADER, unexpectedFrameError.getReceivedFrame().type);
            assertEquals(AMQP.FRAME_METHOD, unexpectedFrameError.getExpectedFrameType());
            return;
        }
        
        fail("No UnexpectedFrameError thrown");
    }
    
    public void testMethodThenBody() throws Exception {
        List<Frame> frames = new ArrayList<Frame>();
        
        byte[] contentBody = new byte[10];
        int channelNumber = 0;
        
        Publish method = new Publish(1, "test", "test", false, false);

        frames.add(method.toFrame(0));
        frames.add(Frame.fromBodyFragment(channelNumber, contentBody, 0, contentBody.length));
        
        myFrameHandler.setFrames(frames.iterator());
 
        try {
            new AMQConnection(params, false, myFrameHandler);
        } catch (IOException e) {
            UnexpectedFrameError unexpectedFrameError = findUnexpectedFrameError(e);
            assertNotNull(unexpectedFrameError);
            assertEquals(AMQP.FRAME_BODY, unexpectedFrameError.getReceivedFrame().type);
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
            return "MyFrameHandler";
        }

        public int getPort() {
            return -1;
        }
    }

}
