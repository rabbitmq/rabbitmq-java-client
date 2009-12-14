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
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.Method;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.UnexpectedFrameError;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.RedirectException;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.LongStringHelper;
import com.rabbitmq.client.impl.AMQImpl.Basic.Publish;


public class BrokenFramesTest extends TestCase {

    public static void main(String args[]) {
        junit.textui.TestRunner.main(new String[] {"com.rabbitmq.client.test.BrokenFramesTest"});
    }

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

    // [1]NB: These test cases only avoid a race because
    // AMQConnection.start() won't return until it's done protocol
    // negotiation.  This makes them a bit brittle.  One way or
    // another, AMQConnection *should* block until it's ready to use;
    // however, it may behave differently during protocol negotiation
    // to during regular operation.  Be wary.
    
    public void testNoMethod() throws Exception {
        List<Frame> frames = new ArrayList<Frame>();
        frames.add(new Frame(AMQP.FRAME_HEADER, 0));
        myFrameHandler.setFrames(frames.iterator());

        AMQConnection conn = new AMQConnection(params, myFrameHandler);
        try {
            conn.start(false);
        } catch (IOException e) {
            UnexpectedFrameError unexpectedFrameError = findUnexpectedFrameError(e); 
            assertNotNull("Expected UnexpectedFrameError", unexpectedFrameError);
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
            new AMQConnection(params, myFrameHandler).start(false);
        } catch (IOException e) {
            UnexpectedFrameError unexpectedFrameError = findUnexpectedFrameError(e);
            assertNotNull(unexpectedFrameError);
            assertEquals(AMQP.FRAME_BODY, unexpectedFrameError.getReceivedFrame().type);
            assertEquals(AMQP.FRAME_HEADER, unexpectedFrameError.getExpectedFrameType());
            return;
        }
        
        fail("No UnexpectedFrameError thrown");
    }

    protected void checkFor501(AMQConnection conn) throws IOException {
        // must be 501 Frame error somewhere here
        assertFalse("Expect connection to be closed", conn.isOpen());
        Command c = myFrameHandler.getLastCompletedCommand();
        assertTrue("Expect Connection.Close method, got " + myFrameHandler.getCompletedCommands().toString(), c.getMethod() instanceof AMQP.Connection.Close);
        assertEquals(AMQP.COMMAND_INVALID,
                     ((AMQP.Connection.Close) c.getMethod()).getReplyCode());
    }
    
    public void testNonZeroChannelHeartbeat() throws Exception {
        // See [1]: Heartbeats don't officially start until after
        // protocol negotiation. (Specifically, the client should
        // start monitoring heartbeats after it receives
        // Connection.Open).
        Frame bogusHeartbeat = new Frame(AMQP.FRAME_HEARTBEAT, 1);
        ArrayList<Frame> frames = negotiation();
        frames.add(bogusHeartbeat);
        myFrameHandler.setFrames(frames.iterator());
        AMQConnection conn = new AMQConnection(params, myFrameHandler);
        conn.start(false);
        // Sadly, there's a race here, since start will return
        // after doing the negotiation.  But we can give the
        // frame-handling loop a big head start.
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ie) {
            // well maybe that was long enough ..
        }
        // must be 501 Frame error somewhere here
        checkFor501(conn);
    }

    public void testNonZeroChannelConnectionMethod() throws Exception {
        // do negotiation then hit it with a Connection method on a
        // channel other than zero
        List<Frame> frames = negotiation();
        frames.add(new WaitForWrite());
        frames.add(new AMQImpl.Channel.OpenOk().toFrame(1));
        // importantly, Connection.Close is plausible after negotiation
        frames.add(new AMQImpl.Connection.Close(200, "OK", 0, 0).toFrame(1));
        myFrameHandler.setFrames(frames.iterator());
        AMQConnection conn = new AMQConnection(params, myFrameHandler);
        try {
            conn.start(false);
            conn.createChannel(1);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                // well maybe that was long enough ..
            }
        }
        catch (IOException shutdown) {
        }
        checkFor501(conn);
    }

    public void testZeroChannelNonConnectionMethod() throws Exception {
        List<Frame> frames = negotiation();
        frames.add(new WaitForWrite());
        frames.add(new AMQImpl.Channel.OpenOk().toFrame(0));        
        myFrameHandler.setFrames(frames.iterator());
        AMQConnection conn = new AMQConnection(params, myFrameHandler);
        try {
            conn.start(false);
            // this will block, and the exception is thrown before it returns
            conn.createChannel(1);
            fail("Expected to have shutdown exception inside RPC");
        }
        catch (IOException shutdown) {
            // this should be the shutdown --
            // ignore and check that everything happened correctly.
            // There is a race here with the frame handler getting the close frame;
            // try to mitigate that by sleeping a bit
            try {Thread.sleep(200);}
            catch (InterruptedException ie) {}
        }
        checkFor501(conn);
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

    ArrayList<Frame> negotiation() throws IOException {
        Frame[] negotiationFrames = new Frame[] {
            new AMQImpl.Connection.Start(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR,
                                         new HashMap<String, Object>(),
                                         LongStringHelper.asLongString("PLAIN"),
                                         LongStringHelper.asLongString("en_US")).toFrame(0),
            new WaitForWrite(), // startOK
            new AMQImpl.Connection.Tune(0, 0, 1).toFrame(0),
            new WaitForWrite(), // tuneOK
            new WaitForWrite(), // open
            new AMQImpl.Connection.OpenOk("").toFrame(0)
        };
        ArrayList<Frame> frames = new ArrayList<Frame>();
        for (Frame f : negotiationFrames) frames.add(f);
        return frames;
    }
    
    // Use this to signal that the frame handler should wait until it's
    // been written to.
    private static class WaitForWrite extends Frame {
    }
    
    private static class MyFrameHandler implements FrameHandler {
    	private Iterator<Frame> frames;

        private LinkedBlockingQueue<Frame> writtenFrames = new LinkedBlockingQueue<Frame>();
        private List<Frame> allWrittenFrames = new ArrayList<Frame>();

        public void setFrame(Frame frame) {
            ArrayList<Frame> frames = new ArrayList();
            frames.add(frame);
            setFrames(frames.iterator());
        }
        
    	public void setFrames(Iterator<Frame> frames) {
            this.frames = frames;
        }

        public List<Frame> getWrittenFrames() {
            return allWrittenFrames;
        }

        public Frame getLastWrittenFrame() {
            synchronized (allWrittenFrames) {
                int last = allWrittenFrames.size() - 1;
                return (last < 0) ? null : allWrittenFrames.get(last);
            }
        }

        public Command getLastCompletedCommand() throws IOException {
            List<Command> cmds = getCompletedCommands();
            int last = cmds.size() - 1;
            return (last < 0) ? null : cmds.get(last);
        }

        public List<Command> getCompletedCommands() throws IOException {
            List<Command> cmds = new ArrayList<Command>();
            AMQCommand.Assembler asm = AMQCommand.newAssembler();
            synchronized (allWrittenFrames) {
                for (Frame f : allWrittenFrames) {
                    asm.handleFrame(f);
                    Command c = asm.completedCommand();
                    if (c != null) {
                        cmds.add(c);
                        asm = AMQCommand.newAssembler(); 
                    }
                }
            }
            return cmds;
        }
        
        public Frame readFrame() throws IOException {
            Frame next = frames.next();
            if (next instanceof WaitForWrite) {
                Frame w = null;
                while (w == null) {
                    try {
                        w = writtenFrames.take();
                    }
                    catch (InterruptedException ie) {
                    }
                }
                return readFrame();
            }
            else {
                //System.out.println("< " + next.toString());
                return next;
            }
        }

        public void sendHeader() throws IOException {
        }

        public void setTimeout(int timeoutMs) throws SocketException {
            // no need to implement this: don't bother changing the timeout
        }

        public void writeFrame(Frame frame) throws IOException {
            synchronized (allWrittenFrames) { allWrittenFrames.add(frame); }
            //System.out.println("> " + frame.toString());
            boolean written = false;
            while (!written) {
                try {
                    writtenFrames.put(frame);
                    written = true;
                }
                catch (InterruptedException ie) {
                }
            }
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
