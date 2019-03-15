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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.SocketFrameHandler;

import javax.net.SocketFactory;


/**
 * Check that protocol negotiation works
 */
public class ConnectionOpen {
    @Test public void correctProtocolHeader() throws IOException {
        SocketFrameHandler fh = new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", AMQP.PROTOCOL.PORT));
        fh.sendHeader();
        AMQCommand command = new AMQCommand();
        while (!command.handleFrame(fh.readFrame())) { }
        Method m = command.getMethod();

        assertTrue("First command must be Connection.start",
                m instanceof AMQP.Connection.Start);
        AMQP.Connection.Start start = (AMQP.Connection.Start) m;
        assertTrue("Version in Connection.start is <= what we sent",
                start.getVersionMajor() < AMQP.PROTOCOL.MAJOR ||
                        (start.getVersionMajor() == AMQP.PROTOCOL.MAJOR &&
                                start.getVersionMinor() <= AMQP.PROTOCOL.MINOR));
    }

    @Test public void crazyProtocolHeader() throws IOException {
        ConnectionFactory factory = TestUtils.connectionFactory();
        // keep the frame handler's socket
        Socket fhSocket = SocketFactory.getDefault().createSocket("localhost", AMQP.PROTOCOL.PORT);
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
            }
        }
    }

    @Test public void frameMaxLessThanFrameMinSize() throws IOException, TimeoutException {
        ConnectionFactory factory = TestUtils.connectionFactory();
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
