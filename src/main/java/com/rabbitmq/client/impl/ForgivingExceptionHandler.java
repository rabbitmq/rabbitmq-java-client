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

import com.rabbitmq.client.*;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;

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
    @Override
    public void handleUnexpectedConnectionDriverException(Connection conn, Throwable exception) {
        log("An unexpected connection driver error occured", exception);
    }

    @Override
    public void handleReturnListenerException(Channel channel, Throwable exception) {
        handleChannelKiller(channel, exception, "ReturnListener.handleReturn");
    }

    @Override
    public void handleConfirmListenerException(Channel channel, Throwable exception) {
        handleChannelKiller(channel, exception, "ConfirmListener.handle{N,A}ck");
    }

    @Override
    public void handleBlockedListenerException(Connection connection, Throwable exception) {
        handleConnectionKiller(connection, exception, "BlockedListener");
    }

    @Override
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
    @Override
    public void handleConnectionRecoveryException(Connection conn, Throwable exception) {
        // ignore java.net.ConnectException as those are
        // expected during recovery and will only produce noisy
        // traces
        if (exception instanceof ConnectException) {
            // no-op
        } else {
            log("Caught an exception during connection recovery!", exception);
        }
    }

    /**
     * @since 3.3.0
     */
    @Override
    public void handleChannelRecoveryException(Channel ch, Throwable exception) {
        log("Caught an exception when recovering channel " + ch.getChannelNumber(), exception);
    }

    /**
     * @since 3.3.0
     */
    @Override
    public void handleTopologyRecoveryException(Connection conn, Channel ch, TopologyRecoveryException exception) {
        log("Caught an exception when recovering topology " + exception.getMessage(), exception);
    }

    protected void handleChannelKiller(Channel channel, Throwable exception, String what) {
        log(what + "threw an exception for channel "+channel, exception);
    }

    protected void handleConnectionKiller(Connection connection, Throwable exception, String what) {
        log(what + " threw an exception for connection " + connection, exception);
        try {
            connection.close(AMQP.REPLY_SUCCESS, "Closed due to exception from " + what);
        } catch (AlreadyClosedException ace) {
            // noop
        } catch (IOException ioe) {
            log("Failure during close of connection " + connection + " after " + exception, ioe);
            connection.abort(AMQP.INTERNAL_ERROR, "Internal error closing connection for " + what);
        }
    }

    protected void log(String message, Throwable e) {
        if(isSocketClosedOrConnectionReset(e)) {
            // we don't want to get too dramatic about those
            LoggerFactory.getLogger(ForgivingExceptionHandler.class).warn(
                message + " (Exception message: "+e.getMessage() + ")"
            );
        } else {
            LoggerFactory.getLogger(ForgivingExceptionHandler.class).error(
                message, e
            );
        }
    }


    private static boolean isSocketClosedOrConnectionReset(Throwable e) {
        return e instanceof IOException &&
            ("Connection reset".equals(e.getMessage()) || "Socket closed".equals(e.getMessage()) ||
                "Connection reset by peer".equals(e.getMessage())
            );
    }

}
