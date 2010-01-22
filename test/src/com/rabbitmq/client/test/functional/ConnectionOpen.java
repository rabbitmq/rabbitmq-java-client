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

package com.rabbitmq.client.test.functional;

import java.io.IOException;
import java.io.DataInputStream;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.impl.Method;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionParameters;

import junit.framework.TestCase;


/**
 * Check that protocol negotiation works
 */
public class ConnectionOpen extends TestCase
{
  public void testCorrectProtocolHeader() throws IOException {
    ConnectionFactory factory = new ConnectionFactory();
    SocketFrameHandler fh = new SocketFrameHandler(factory.getSocketFactory().createSocket("localhost", AMQP.PROTOCOL.PORT));
    fh.sendHeader();
    AMQCommand.Assembler a = AMQCommand.newAssembler();
    AMQCommand command = null;
    while (command==null) {
      command = a.handleFrame(fh.readFrame());
    }
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
    SocketFrameHandler fh = new SocketFrameHandler(factory.getSocketFactory().createSocket("localhost", AMQP.PROTOCOL.PORT));
    fh.sendHeader(100, 3); // major, minor
    DataInputStream in = fh._inputStream;
    // we should get a valid protocol header back
    byte[] header = new byte[4];
    in.read(header);
    // The protocol header is "AMQP" plus a version that the server
    // supports.  We can really only test for the first bit.
    assertEquals("AMQP", new String(header));
    in.read(header);
    assertEquals(in.available(), 0);
    // At this point the socket should have been closed.  We can't
    // directly test for this, since Socket.isClosed isn't very
    // reliable, but we can test whether trying to read more bytes
    // gives an error.
    fh._socket.setSoTimeout(500);
    // NB the frame handler will return null if the socket times out
    try {
      fh.readFrame();
      fail("Expected socket read to fail due to socket being closed");
    }
    catch (MalformedFrameException mfe) {
      fail("Expected nothing, rather than a badly-formed something");
    }
    catch (IOException ioe) {
      return;
    }
  }

  public void testFrameMaxLessThanFrameMinSize() throws IOException {
    ConnectionParameters params = new ConnectionParameters();
    params.setRequestedFrameMax(100);
    ConnectionFactory factory = new ConnectionFactory(params);
    try {
      factory.newConnection("localhost");
    }
    catch (IOException ioe) {
      return;
    }
    fail("Broker should have closed the connection since our frame max < frame_min_size");
  }
  
}
