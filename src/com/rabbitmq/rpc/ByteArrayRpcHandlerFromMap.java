// The contents of this file are subject to the Mozilla Public License
// Version 1.1 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License
// at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS"
// basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
// the License for the specific language governing rights and
// limitations under the License.
//
// The Original Code is RabbitMQ.
//
// The Initial Developer of the Original Code is VMware, Inc.
// Copyright (c) 2011 VMware, Inc.  All rights reserved.
//
package com.rabbitmq.rpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.MethodArgumentReader;
import com.rabbitmq.client.impl.MethodArgumentWriter;
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;

/**
 * An {@link RpcHandler RpcHandler&lt;byte[], byte[]&gt;} which delegates to an injected
 * {@link RpcHandler RpcHandler&lt;Map, Map&gt;} converting to and from byte arrays using the AMQP
 * wire-format for table encoding. Conversion errors are reflected as Rpc run-time exceptions.
 */
public class ByteArrayRpcHandlerFromMap implements RpcHandler<byte[], byte[]> {
    private final RpcHandler<Map<String, Object>, Map<String, Object>> delegateHandler;

    /**
     * Create a new {@link RpcHandler RpcHandler&lt;byte[], byte[]&gt;} which converts and delegates
     * to a {@link RpcHandler RpcHandler&lt;Map, Map&gt;}
     *
     * @param delegateHandler to delegate to
     */
    public ByteArrayRpcHandlerFromMap(
            RpcHandler<Map<String, Object>, Map<String, Object>> delegateHandler) {
        this.delegateHandler = delegateHandler;
    }

    public byte[] handleCall(Envelope envelope, BasicProperties requestProps, byte[] parm,
            BasicProperties replyProps) {
        return encode(this.delegateHandler.handleCall(envelope, requestProps, decode(parm),
                replyProps));
    }

    public void handleCast(Envelope envelope, BasicProperties requestProps, byte[] parm) {
        this.delegateHandler.handleCast(envelope, requestProps, decode(parm));
    }

    private static Map<String, Object> decode(byte[] body) {
        try {
            MethodArgumentReader reader = new MethodArgumentReader(new ValueReader(new DataInputStream(
                    new ByteArrayInputStream(body))));
            Map<String, Object> request = reader.readTable();
            return request;
        } catch (IOException e) {
            throw RpcException.newRpcException("Decoding conversion error.", e);
        }
    }

    private static byte[] encode(Map<String, Object> reply) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(
                    new DataOutputStream(buffer)));
            writer.writeTable(reply);
            writer.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw RpcException.newRpcException("Encoding conversion error.", e);
        }
    }
}
