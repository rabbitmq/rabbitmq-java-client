// Copyright (c) 2017-Present Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.ShutdownSignalException;

/**
 *
 */
public class RpcContinuationRpcWrapper implements RpcWrapper {

    private final AMQChannel.RpcContinuation continuation;

    public RpcContinuationRpcWrapper(AMQChannel.RpcContinuation continuation) {
        this.continuation = continuation;
    }

    @Override
    public boolean canHandleReply(AMQCommand command) {
        return continuation.canHandleReply(command);
    }

    @Override
    public void complete(AMQCommand command) {
        continuation.handleCommand(command);
    }

    @Override
    public void shutdown(ShutdownSignalException signal) {
        continuation.handleShutdownSignal(signal);
    }
}
