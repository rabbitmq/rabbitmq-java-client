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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * An implementation of {@link com.rabbitmq.client.ExceptionHandler} that does
 * close channels on unhandled consumer exception.
 *
 * Used by {@link AMQConnection}.
 *
 * @see ExceptionHandler
 * @see com.rabbitmq.client.ConnectionFactory#setExceptionHandler(ExceptionHandler)
 */
public class StrictExceptionHandler extends ForgivingExceptionHandler implements ExceptionHandler {
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
        String logMessage = "Consumer " + consumer
            + " (" + consumerTag + ")"
            + " method " + methodName
            + " for channel " + channel;
        String closeMessage = "Consumer"
            + " (" + consumerTag + ")"
            + " method " + methodName
            + " for channel " + channel;
        handleChannelKiller(channel, exception, logMessage, closeMessage);
    }

    @Override
    protected void handleChannelKiller(Channel channel, Throwable exception, String what) {
        handleChannelKiller(channel, exception, what, what);
    }

    protected void handleChannelKiller(Channel channel, Throwable exception, String logMessage, String closeMessage) {
        log(logMessage + " threw an exception for channel " + channel, exception);
        try {
            channel.close(AMQP.REPLY_SUCCESS, "Closed due to exception from " + closeMessage);
        } catch (AlreadyClosedException ace) {
            // noop
        } catch (TimeoutException ace) {
            // noop
        } catch (IOException ioe) {
            log("Failure during close of channel " + channel + " after " + exception, ioe);
            channel.getConnection().abort(AMQP.INTERNAL_ERROR, "Internal error closing channel for " + closeMessage);
        }
    }



}
