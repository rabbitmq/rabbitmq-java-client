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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.net.Socket;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;

/* Publish a message of size FRAME_MAX.  The broker should split this
 * into two frames before sending back. */
public class FrameMax extends BrokerTestCase {
    /* This value for FrameMax is larger than the minimum and less
     * than what Rabbit suggests. */
    final static int FRAME_MAX = 70000;
    final static int REAL_FRAME_MAX = FRAME_MAX - 8;
    final static String ROUTING_KEY = "something";

    private String queueName;

    public FrameMax() {
        connectionFactory = new MyConnectionFactory();
        connectionFactory.setRequestedFrameMax(FRAME_MAX);
    }

    @Override
    protected void createResources()
        throws IOException
    {
        queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, "", ROUTING_KEY);
    }

    /* Frame content should be less or equal to frame-max - 8. */
    public void testFrameSizes()
        throws IOException, InterruptedException
    {
        /* This should result in at least 3 frames. */
        int howMuch = 2*FRAME_MAX;
        basicPublishVolatile(new byte[howMuch], ROUTING_KEY);
        /* Receive everything that was sent out. */
        while (howMuch > 0) {
            try {
                GetResponse response = channel.basicGet(queueName, false);
                howMuch -= response.getBody().length;
            } catch (Exception e) {
                fail(e.getCause().toString());
            }
        }
    }

    /* ConnectionFactory that uses MyFrameHandler rather than
     * SocketFrameHandler. */
    private static class MyConnectionFactory extends ConnectionFactory {
        protected FrameHandler createFrameHandler(Socket sock)
            throws IOException
        {
            return new MyFrameHandler(sock);
        }
    }

    /* FrameHandler with added frame-max error checking. */
    private static class MyFrameHandler extends SocketFrameHandler {
        public MyFrameHandler(Socket socket)
            throws IOException
        {
            super(socket);
        }

        public Frame readFrame() throws IOException {
            Frame f = super.readFrame();
            int size = f.getPayload().length;
            if (size > REAL_FRAME_MAX)
                fail("Received frame of size " + size
                     + ", which exceeds " + REAL_FRAME_MAX + ".");
            //System.out.printf("Received a frame of size %d.\n", f.getPayload().length);
            return f;
        }
    }
}
