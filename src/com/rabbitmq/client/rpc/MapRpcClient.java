// The contents of this file are subject to the Mozilla Public License
//Version 1.1 (the "License"); you may not use this file except in
//compliance with the License. You may obtain a copy of the License
//at http://www.mozilla.org/MPL/
//
//Software distributed under the License is distributed on an "AS IS"
//basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//the License for the specific language governing rights and
//limitations under the License.
//
//The Original Code is RabbitMQ.
//
//The Initial Developer of the Original Code is VMware, Inc.
//Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.rpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.LongString;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.MethodArgumentReader;
import com.rabbitmq.client.impl.MethodArgumentWriter;
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;

/**
 * An AMQP wire-protocol table RPC client.
 * <p/>
 * This class delegates RPC calls to a {@link RpcCaller RpcCaller&lt;byte[], byte[]&gt;} injected
 * into the constructor, and translates tables (maps from {@link String} to {@link Object}) into and
 * from byte arrays using AMQP wire-protocol table encoding.
 * <p/>
 * The values in the table must be of type {@link String}, {@link LongString},
 * {@link Integer}, {@link java.math.BigDecimal BigDecimal}, {@link Date}, or (recursively) a
 * {@link Map} from {@link String}s to these types.
 * <p/>
 * <b>Concurrency Semantics</b><br/>
 * The class is thread-safe, if the delegate {@link RpcCaller} is thread-safe.
 */
public class MapRpcClient implements RpcClient<Map<String, Object>, Map<String, Object>> {

    private final RpcCaller<byte[], byte[]> rpcCaller;
    private final String exchange;
    private final String routingKey;

    /**
     * Construct an {@link RpcClient} which calls a fixed RPC Server (identified by
     * <code>exchange</code> and <code>routingKey</code>) using the supplied {@link RpcCaller
     * RpcCaller&lt;byte[], byte[]&gt;}.
     *
     * @param exchange to supply to caller
     * @param routingKey to supply to caller
     * @param rpcCaller to call remote procedure with
     */
    public MapRpcClient(String exchange, String routingKey, RpcCaller<byte[], byte[]> rpcCaller) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.rpcCaller = rpcCaller;
    }

    public Map<String, Object> call(Map<String, Object> request) throws IOException,
            TimeoutException, ShutdownSignalException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(
                new DataOutputStream(buffer)));
        writer.writeTable(request);
        writer.flush();
        byte[] reply = this.rpcCaller.call(this.exchange, this.routingKey, buffer.toByteArray());
        MethodArgumentReader reader = new MethodArgumentReader(new ValueReader(new DataInputStream(
                new ByteArrayInputStream(reply))));
        return reader.readTable();
    }
}
