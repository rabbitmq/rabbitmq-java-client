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
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;

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
    @Override
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
            new MethodArgumentReader(new ValueReader
                                     (new DataInputStream
                                      (new ByteArrayInputStream(requestBody))));
        Map<String, Object> request = reader.readTable();
        return request;
    }

    public static byte[] encode(Map<String, Object> reply)
        throws IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(new DataOutputStream(buffer)));
        writer.writeTable(reply);
        writer.flush();
        return buffer.toByteArray();
    }

    /**
     * Delegates to {@link MapRpcServer#handleMapCall(Map)}.
     */
    public Map<String, Object> handleMapCall(Map<String, Object> request,
                                             AMQP.BasicProperties replyProperties)
    {
        return handleMapCall(request);
    }

    /**
     * Default implementation override in subclasses. Returns the empty string.
     */
    public Map<String, Object> handleMapCall(Map<String, Object> request)
    {
        return new HashMap<String, Object>();
    }

    /**
     * Overridden to delegate to handleMapCast.
     */
    @Override
    public void handleCast(byte[] requestBody)
    {
        try {
            handleMapCast(decode(requestBody));
        } catch (IOException ioe) {
            // Do nothing.
        }
    }

    /**
     * Default implementation override in subclasses. Does nothing.
     */
    public void handleMapCast(Map<String, Object> requestBody) {
        // Do nothing.
    }
}
