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
package com.rabbitmq.client.rpc;

import java.io.IOException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Envelope;

/**
 * An {@link RpcHandler RpcHandler&lt;byte[], byte[], IOException&gt;} which delegates to an
 * injected {@link RpcHandler RpcHandler&lt;String, String, IOException&gt;}
 */
public class ByteArrayRpcHandlerFromString implements
        RpcHandler<byte[], byte[]> {

    private final RpcHandler<String, String> delegateHandler;

    public ByteArrayRpcHandlerFromString(
            RpcHandler<String, String> delegateHandler) {
        this.delegateHandler = delegateHandler;
    }

    public byte[] handleCall(Envelope envelope, BasicProperties requestProps,
            byte[] parm, BasicProperties replyProps) throws IOException {
        return this.delegateHandler.handleCall(envelope, requestProps,
                new String(parm, "UTF-8"), replyProps).getBytes("UTF-8");
    }

    public void handleCast(Envelope envelope, BasicProperties requestProps,
            byte[] parm) throws IOException {
        this.delegateHandler.handleCast(envelope, requestProps, new String(
                parm, "UTF-8"));
    }

}
