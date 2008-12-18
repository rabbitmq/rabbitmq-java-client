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

package com.rabbitmq.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.impl.MethodArgumentReader;
import com.rabbitmq.client.impl.MethodArgumentWriter;

/**
 * Subclass of RpcServer which uses AMQP wire-format encoded tables as
 * requests and replies.
 */
public class MapRpcServer extends RpcServer {
    public MapRpcServer(Channel channel) throws IOException
    { super(channel); }

    public MapRpcServer(Channel channel, String queueName) throws IOException
    { super(channel, queueName); }

    /**
     * Overridden to delegate to handleMapCall.
     */
    public byte[] handleCall(byte[] requestBody, AMQP.BasicProperties replyProperties)
    {
        try {
            return encode(handleMapCall(decode(requestBody), replyProperties));
        } catch (IOException ioe) {
            return new byte[0];
        }
    }

    public static Map<String, Object> decode(byte[] requestBody)
        throws IOException
    {
        MethodArgumentReader reader =
            new MethodArgumentReader(new DataInputStream
                                     (new ByteArrayInputStream(requestBody)));
        Map<String, Object> request = reader.readTable();
        return request;
    }

    public static byte[] encode(Map<String, Object> reply)
        throws IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new DataOutputStream(buffer));
        writer.writeTable(reply);
        writer.flush();
        return buffer.toByteArray();
    }

    /**
     * Delegates to handleMapCall(Map<String, Object>).
     */
    public Map<String, Object> handleMapCall(Map<String, Object> request,
                                             AMQP.BasicProperties replyProperties)
    {
        return handleMapCall(request);
    }

    /**
     * Default implementation - override in subclasses. Returns the empty string.
     */
    public Map<String, Object> handleMapCall(Map<String, Object> request)
    {
        return new HashMap<String, Object>();
    }

    /**
     * Overridden to delegate to handleMapCast.
     */
    public void handleCast(byte[] requestBody)
    {
        try {
            handleMapCast(decode(requestBody));
        } catch (IOException ioe) {
            // Do nothing.
        }
    }

    /**
     * Default implementation - override in subclasses. Does nothing.
     */
    public void handleMapCast(Map<String, Object> requestBody) {
        // Do nothing.
    }
}
