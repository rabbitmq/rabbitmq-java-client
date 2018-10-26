// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client;

/**
 * Exception thrown when a RPC request isn't routed to any queue.
 * <p>
 * The {@link RpcClient} must be configured with the mandatory
 * flag set to true with {@link RpcClientParams#useMandatory()}.
 *
 * @see RpcClientParams#useMandatory()
 * @see RpcClient#RpcClient(RpcClientParams)
 * @since 5.6.0
 */
public class UnroutableRpcRequestException extends RuntimeException {

    private final Return returnMessage;

    public UnroutableRpcRequestException(Return returnMessage) {
        this.returnMessage = returnMessage;
    }

    /**
     * The returned message.
     *
     * @return
     */
    public Return getReturnMessage() {
        return returnMessage;
    }
}
