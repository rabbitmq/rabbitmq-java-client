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

package com.rabbitmq.client.facilities;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * A {@link String} based RPC client.
 * <p/>
 * This class delegates to a {@link RpcClient RpcClient&lt;byte[], byte[]&gt;}, and translates {@link String}s into and
 * from byte arrays, using UTF-8 encoding.
 * <p/>
 * The client delegates to a byte-array RPC client, initialised in the constructor.
 * <p/>
 * <b>Concurrency Semantics</b><br/>
 * The class is thread-safe, if the delegate is thread-safe.
 */
public class StringRpcClient implements RpcClient<String, String> {

    private final RpcClient<byte[], byte[]> rpcClient;

    public StringRpcClient(RpcClient<byte[], byte[]> rpcClient) {
        this.rpcClient = rpcClient;
    }

    public String call(String exchange, String routingKey, String request)
            throws IOException, TimeoutException, ShutdownSignalException {
        return new String(this.rpcClient.call(exchange, routingKey,
                request.getBytes("UTF-8")), "UTF-8");
    }

    public void close() throws IOException {
        this.rpcClient.close();
    }
}
