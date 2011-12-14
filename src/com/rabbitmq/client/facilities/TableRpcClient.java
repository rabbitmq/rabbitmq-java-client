//The contents of this file are subject to the Mozilla Public License
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

package com.rabbitmq.client.facilities;

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
 * This class delegates RPC calls to a {@link RpcClient RpcClient&lt;byte[], byte[]&gt;}, and translates tables (maps
 * from {@link String} to {@link Object}) into and from byte arrays using AMQP wire-protocol table encoding. There are some restrictions on
 * the values appearing in the table: <br>
 * they must be of type {@link String}, {@link LongString}, {@link Integer},
 * {@link java.math.BigDecimal}, {@link Date}, or (recursively) a {@link Map} of the enclosing type.
 * <p/>
 * The client delegates to a byte-array RPC client, initialised in the constructor.
 * <p/>
 * <b>Concurrency Semantics</b><br/>
 * The class is thread-safe, if the delegate {@link RpcClient} is thread-safe.
 */
public class TableRpcClient implements
        RpcClient<Map<String, Object>, Map<String, Object>> {

    private final RpcClient<byte[], byte[]> rpcClient;

    public TableRpcClient(RpcClient<byte[], byte[]> rpcClient) {
        this.rpcClient = rpcClient;
    }

    public Map<String, Object> call(String exchange, String routingKey,
            Map<String, Object> request) throws IOException, TimeoutException,
            ShutdownSignalException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(
                new DataOutputStream(buffer)));
        writer.writeTable(request);
        writer.flush();
        byte[] reply = this.rpcClient.call(exchange, routingKey,
                buffer.toByteArray());
        MethodArgumentReader reader = new MethodArgumentReader(new ValueReader(
                new DataInputStream(new ByteArrayInputStream(reply))));
        return reader.readTable();
    }

    public void close() throws IOException {
        this.rpcClient.close();
    }
}
