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
 * Interface to an exception-handling object.
 */
public interface ExceptionHandler {
    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has an exception
     * signalled to it that it can't otherwise deal with.
     * @param conn the Connection that caught the exception
     * @param exception the exception caught in the driver thread
     */
    void handleUnexpectedConnectionDriverException(Connection conn, Throwable exception);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has called a
     * ReturnListener's handleReturn method, and that method has
     * thrown an exception.
     * @param channel the ChannelN that held the ReturnListener
     * @param exception the exception thrown by ReturnListener.handleReturn
     */
    void handleReturnListenerException(Channel channel, Throwable exception);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has called a
     * ConfirmListener's handleAck or handleNack method, and that
     * method has thrown an exception.
     * @param channel the ChannelN that held the ConfirmListener
     * @param exception the exception thrown by ConfirmListener.handleAck
     */
    void handleConfirmListenerException(Channel channel, Throwable exception);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has called a
     * BlockedListener's method, and that method has
     * thrown an exception.
     * @param connection the Connection that held the BlockedListener
     * @param exception the exception thrown by the BlockedListener
     */
    void handleBlockedListenerException(Connection connection, Throwable exception);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has called a method
     * on a Consumer, and that method has thrown an exception.
     * @param channel the ChannelN that held the Consumer
     * @param exception the exception thrown by the Consumer
     * @param consumer the Consumer that caused the fault
     * @param consumerTag the Consumer's consumerTag
     * @param methodName the name of the method on the Consumer that threw the exception
     */
    void handleConsumerException(Channel channel,
                                 Throwable exception,
                                 Consumer consumer,
                                 String consumerTag,
                                 String methodName);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has an exception
     * during connection recovery that it can't otherwise deal with.
     * @param conn the Connection that caught the exception
     * @param exception the exception caught in the driver thread
     */
    void handleConnectionRecoveryException(Connection conn, Throwable exception);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has an exception
     * during channel recovery that it can't otherwise deal with.
     * @param ch the Channel that caught the exception
     * @param exception the exception caught in the driver thread
     */
    void handleChannelRecoveryException(Channel ch, Throwable exception);

    /**
     * Perform any required exception processing for the situation
     * when the driver thread for the connection has an exception
     * during topology (exchanges, queues, bindings, consumers) recovery
     * that it can't otherwise deal with.
     * @param conn the Connection that caught the exception
     * @param ch the Channel that caught the exception. May be null.
     * @param exception the exception caught in the driver thread
     */

    void handleTopologyRecoveryException(Connection conn, Channel ch, TopologyRecoveryException exception);
}
