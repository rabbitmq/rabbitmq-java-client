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
 * A Rabbit RpcClient identifies a <i>Remote Procedure</i> to <i>Call</i> by an
 * <code>exchange</code> and <code>routingKey</code> <code>String</code> pair and can
 * {@link RpcClient#call call} with a single parameter of generic type <code>T</code>. The result is
 * also of generic type <code>R</code>.
 * <p/>
 * <b>Concurrent Semantics</b><br/>
 * Implementations must be thread-safe and may or may not permit interleaved requests.
 * @param <P> the type of the parameter
 * @param <R> the type of the response
 */
public interface RpcClient<P,R> {
    /**
     * Perform a Remote Procedure Call, blocking until a response is
     * received.
     * @param exchange to which RPC is sent
     * @param routingKey for request on exchange
     * @param request the request to send
     * @return the response received
     * @throws ShutdownSignalException if the connection or channel dies before a response is
     *             received.
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a response is not received within the configured timeout
     */
    R call(String exchange, String routingKey, P request) throws IOException,
            TimeoutException, ShutdownSignalException;

    /**
     * Close the client. All external resources are now freed, and the client cannot be used after
     * this call.
     * @throws IOException if an error is encountered
     */
    void close() throws IOException;
}
