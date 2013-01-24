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

import java.io.IOException;
import java.io.DataInputStream;
import java.net.Socket;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.ConnectionFactory;

import junit.framework.TestCase;


/**
 * Check that protocol negotiation works
 */
public class ConnectionOpen extends TestCase {
    public void testCorrectProtocolHeader() throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        SocketFrameHandler fh = new SocketFrameHandler(factory.getSocketFactory().createSocket("localhost", AMQP.PROTOCOL.PORT));
        fh.sendHeader();
        AMQCommand command = new AMQCommand();
        while (!command.handleFrame(fh.readFrame())) { }
        Method m = command.getMethod();
        //    System.out.println(m.getClass());
        assertTrue("First command must be Connection.start",
                m instanceof AMQP.Connection.Start);
        AMQP.Connection.Start start = (AMQP.Connection.Start) m;
        assertTrue("Version in Connection.start is <= what we sent",
                start.getVersionMajor() < AMQP.PROTOCOL.MAJOR ||
                        (start.getVersionMajor() == AMQP.PROTOCOL.MAJOR &&
                                start.getVersionMinor() <= AMQP.PROTOCOL.MINOR));
    }

    public void testCrazyProtocolHeader() throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        // keep the frame handler's socket
        Socket fhSocket = factory.getSocketFactory().createSocket("localhost", AMQP.PROTOCOL.PORT);
        SocketFrameHandler fh = new SocketFrameHandler(fhSocket);
        fh.sendHeader(100, 3); // major, minor
        DataInputStream in = fh.getInputStream();
        // we should get a valid protocol header back
        byte[] header = new byte[4];
        in.read(header);
        // The protocol header is "AMQP" plus a version that the server
        // supports.  We can really only test for the first bit.
        assertEquals("AMQP", new String(header));
        in.read(header);
        assertEquals(in.available(), 0);
        // At this point the socket should have been closed.  We can
        // directly test for this, but since Socket.isClosed is purported to be
        // unreliable, we can also test whether trying to read more bytes
        // gives an error.
        if (!fhSocket.isClosed()) {
            fh.setTimeout(500);
            // NB the frame handler will return null if the socket times out
            try {
                fh.readFrame();
                fail("Expected socket read to fail due to socket being closed");
            } catch (MalformedFrameException mfe) {
                fail("Expected nothing, rather than a badly-formed something");
            } catch (IOException ioe) {
                return;
            }
        }
    }

    public void testFrameMaxLessThanFrameMinSize() throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setRequestedFrameMax(100);
        try {
            factory.newConnection();
        }
        catch (IOException ioe) {
            return;
        }
        fail("Broker should have closed the connection since our frame max < frame_min_size");
    }
}
