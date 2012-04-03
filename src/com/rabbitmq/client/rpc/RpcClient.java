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
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * An <code>RpcClient</code> is a mechanism for calling Remote Procedures.
 * <p/>
 * <b>Concurrency Semantics</b><br/>
 * Implementations must be thread-safe, and close will cancel all waits for responses in-flight.
 * Calls and responses may interleave, but callers will block until their results are returned.
 * @param <P> Parameter type
 * @param <R> Response type
 */
public interface RpcClient<P, R> {
    /**
     * Start the mechanism by which calls are made.
     * @throws IOException on mechanism error
     */
    void open() throws IOException;

    /**
     * Call the Procedure identified by the <code>exchange</code> and <code>routingKey</code>.
     * @param exchange the exchange to route the call through
     * @param routingKey the key on the exchange to identify the callee
     * @param parameter the parameter for the procedure call
     * @return the result of the call
     * @throws IOException if a communication error occurs
     * @throws ShutdownSignalException if the connection or channel is shutdown before a result is
     *             returned
     * @throws TimeoutException if no response is received within a given time
     * @throws RpcException (unchecked) if the Rpc system fails, for example, if encoding or
     *             decoding errors occur, or the message is lost
     * @throws ServiceException (unchecked) if the call handler on the server throws an exception
     */
    R call(String exchange, String routingKey, P parameter)
            throws IOException, ShutdownSignalException, TimeoutException;

    /**
     * Close the caller. All calls extant are cancelled.
     * @throws IOException on mechanism error
     */
    void close() throws IOException;
}
