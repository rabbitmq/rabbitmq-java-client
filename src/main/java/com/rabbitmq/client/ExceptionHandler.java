//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

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
     * FlowListener's handleFlow method, and that method has
     * thrown an exception.
     * @param channel the ChannelN that held the FlowListener
     * @param exception the exception thrown by FlowListener.handleFlow
     */
    void handleFlowListenerException(Channel channel, Throwable exception);

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
     * @param ch the Channel that caught the exception
     * @param exception the exception caught in the driver thread
     */

    void handleTopologyRecoveryException(Connection conn, Channel ch, TopologyRecoveryException exception);
}
