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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;

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
     * thrown an exeption.
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
}
