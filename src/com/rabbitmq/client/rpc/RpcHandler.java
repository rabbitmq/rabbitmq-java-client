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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Envelope;

/**
 * Call handler for a (remote) procedure (RPC) with or without a reply.
 * @param <P> the call parameter type
 * @param <R> the result type
 */
public interface RpcHandler<P, R> {

    /**
     * Handle a call expecting a result
     * @param envelope message envelope of request
     * @param requestProperties AMQP properties of the (remote) request
     * @param parm parameter
     * @param replyProperties AMQP properties that will be used on reply (mutable)
     * @return result
     */
    R handleCall(Envelope envelope, BasicProperties requestProperties, P parm,
            BasicProperties replyProperties);

    /**
     * Handle a call <i>not</i> delivering a result
     * @param envelope message envelope of request
     * @param requestProperties AMQP properties of the request
     * @param parm parameter
     */
    void handleCast(Envelope envelope, BasicProperties requestProperties, P parm);
}
