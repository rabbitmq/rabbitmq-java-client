//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
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
     * ReturnListener's handleBasicReturn method, and that method has
     * thrown an exception.
     * @param channel the ChannelN that held the ReturnListener
     * @param exception the exception thrown by ReturnListener.handleBasicReturn
     */
    void handleReturnListenerException(Channel channel, Throwable exception);

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
