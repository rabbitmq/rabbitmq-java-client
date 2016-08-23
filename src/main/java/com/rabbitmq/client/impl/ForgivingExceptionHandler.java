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

package com.rabbitmq.client.impl;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.TopologyRecoveryException;

/**
 * An implementation of {@link com.rabbitmq.client.ExceptionHandler} that does not
 * close channels on unhandled consumer and listener exception.
 * 
 * Used by {@link AMQConnection}.
 *
 * @see ExceptionHandler
 * @see com.rabbitmq.client.ConnectionFactory#setExceptionHandler(com.rabbitmq.client.ExceptionHandler)
 */
public class ForgivingExceptionHandler implements ExceptionHandler {
    public void handleUnexpectedConnectionDriverException(Connection conn, Throwable exception) {
        // TODO: Log this somewhere, just in case we have a bug like
        // 16272 where exceptions aren't being propagated properly
        // again.

        //System.err.println("DefaultExceptionHandler:");
        //exception.printStackTrace();
    }

    public void handleReturnListenerException(Channel channel, Throwable exception) {
        handleChannelKiller(channel, exception, "ReturnListener.handleReturn");
    }

    public void handleFlowListenerException(Channel channel, Throwable exception) {
        handleChannelKiller(channel, exception, "FlowListener.handleFlow");
    }

    public void handleConfirmListenerException(Channel channel, Throwable exception) {
        handleChannelKiller(channel, exception, "ConfirmListener.handle{N,A}ck");
    }

    public void handleBlockedListenerException(Connection connection, Throwable exception) {
        handleConnectionKiller(connection, exception, "BlockedListener");
    }

    public void handleConsumerException(Channel channel, Throwable exception,
                                        Consumer consumer, String consumerTag,
                                        String methodName)
    {
        handleChannelKiller(channel, exception, "Consumer " + consumer
                                              + " (" + consumerTag + ")"
                                              + " method " + methodName
                                              + " for channel " + channel);
    }

    /**
     * @since 3.3.0
     */
    public void handleConnectionRecoveryException(Connection conn, Throwable exception) {
        // ignore java.net.ConnectException as those are
        // expected during recovery and will only produce noisy
        // traces
        if (exception instanceof ConnectException) {
            // no-op
        } else {
            System.err.println("Caught an exception during connection recovery!");
            exception.printStackTrace(System.err);
        }
    }

    /**
     * @since 3.3.0
     */
    public void handleChannelRecoveryException(Channel ch, Throwable exception) {
        System.err.println("Caught an exception when recovering channel " + ch.getChannelNumber());
        exception.printStackTrace(System.err);
    }

    /**
     * @since 3.3.0
     */
    public void handleTopologyRecoveryException(Connection conn, Channel ch, TopologyRecoveryException exception) {
        System.err.println("Caught an exception when recovering topology " + exception.getMessage());
        exception.printStackTrace(System.err);
    }

    protected void handleChannelKiller(Channel channel, Throwable exception, String what) {
        System.err.println(this.getClass().getName() + ": " + what + " threw an exception for channel "
                + channel + ":");
        exception.printStackTrace();
    }

    protected void handleConnectionKiller(Connection connection, Throwable exception, String what) {
        // TODO: log the exception
        System.err.println("DefaultExceptionHandler: " + what + " threw an exception for connection "
                + connection + ":");
        exception.printStackTrace();
        try {
            connection.close(AMQP.REPLY_SUCCESS, "Closed due to exception from " + what);
        } catch (AlreadyClosedException ace) {
            // noop
        } catch (IOException ioe) {
            // TODO: log the failure
            System.err.println("Failure during close of connection " + connection + " after " + exception
                    + ":");
            ioe.printStackTrace();
            connection.abort(AMQP.INTERNAL_ERROR, "Internal error closing connection for " + what);
        }
    }
}
