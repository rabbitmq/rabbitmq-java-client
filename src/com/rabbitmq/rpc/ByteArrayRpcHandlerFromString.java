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

import java.io.UnsupportedEncodingException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Envelope;

/**
 * An {@link RpcHandler RpcHandler&lt;byte[], byte[]&gt;} which delegates to an injected
 * {@link RpcHandler RpcHandler&lt;String, String&gt;}. Conversion errors are reflected as Rpc
 * run-time exceptions.
 */
public class ByteArrayRpcHandlerFromString implements
        RpcHandler<byte[], byte[]> {

    private final RpcHandler<String, String> delegateHandler;

    /**
     * Create a new {@link RpcHandler RpcHandler&lt;byte[], byte[]&gt;} which converts and delegates
     * to a {@link RpcHandler RpcHandler&lt;String, String&gt;}
     * @param delegateHandler to delegate to
     */
    public ByteArrayRpcHandlerFromString(
            RpcHandler<String, String> delegateHandler) {
        this.delegateHandler = delegateHandler;
    }

    public byte[] handleCall(Envelope envelope, BasicProperties requestProps,
            byte[] parm, BasicProperties replyProps) {
        try {
            return this.delegateHandler.handleCall(envelope, requestProps,
                    new String(parm, "UTF-8"), replyProps).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw RpcException.newRpcException("String conversion error.", e);
        }
    }

    public void handleCast(Envelope envelope, BasicProperties requestProps,
            byte[] parm) {
        try {
            this.delegateHandler.handleCast(envelope, requestProps, new String(
                    parm, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw RpcException.newRpcException("String conversion error.", e);
        }
    }

}
