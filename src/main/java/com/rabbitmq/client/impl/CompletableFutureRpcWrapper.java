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

import com.rabbitmq.client.*;
import com.rabbitmq.client.Method;

import java.util.concurrent.CompletableFuture;

/**
 *
 */
public class CompletableFutureRpcWrapper implements RpcWrapper {

    private final com.rabbitmq.client.Method request;

    private final CompletableFuture<Command> completableFuture;

    public CompletableFutureRpcWrapper(Method method, CompletableFuture<Command> completableFuture) {
        this.request = method;
        this.completableFuture = completableFuture;
    }

    @Override
    public boolean canHandleReply(AMQCommand command) {
        return AMQChannel.SimpleBlockingRpcContinuation.isResponseCompatibleWithRequest(request, command.getMethod());
    }

    @Override
    public void complete(AMQCommand command) {
        completableFuture.complete(command);
    }

    @Override
    public void shutdown(ShutdownSignalException signal) {
        completableFuture.completeExceptionally(signal);
    }
}
