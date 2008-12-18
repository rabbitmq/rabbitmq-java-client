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

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;

public class DefaultExceptionHandler implements ExceptionHandler {
    public void handleUnexpectedConnectionDriverException(Connection conn, Throwable exception) {
        // TODO: Log this somewhere, just in case we have a bug like
        // 16272 where exceptions aren't being propagated properly
        // again.

        //System.err.println("DefaultExceptionHandler:");
        //exception.printStackTrace();
    }

    public void handleReturnListenerException(Channel channel, Throwable exception) {
        // TODO: Convert to logging framework
        System.err.println("ReturnListener.handleBasicReturn threw an exception for channel " +
                           channel + ":");
        exception.printStackTrace();
        try {
            ((AMQConnection) channel.getConnection()).close(AMQP.INTERNAL_ERROR,
                                                            "Internal error in ReturnListener",
                                                            false,
                                                            exception);
        } catch (IOException ioe) {
            // Man, this clearly isn't our day.
            // Ignore the exception? TODO: Log the nested failure
        }
    }

    public void handleConsumerException(Channel channel,
                                        Throwable exception,
                                        Consumer consumer,
                                        String consumerTag,
                                        String methodName)
    {
        // TODO: Convert to logging framework
        System.err.println("Consumer " + consumer + " method " + methodName + " for channel " +
                           channel + " threw an exception:");
        exception.printStackTrace();
        try {
            ((AMQConnection) channel.getConnection()).close(AMQP.INTERNAL_ERROR,
                                                            "Internal error in Consumer " +
                                                              consumerTag,
                                                            false,
                                                            exception);
        } catch (IOException ioe) {
            // Man, this clearly isn't our day.
            // Ignore the exception? TODO: Log the nested failure
        }
    }
}
