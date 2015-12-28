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

import java.io.IOException;

/**
 * Subclass of RpcServer which accepts UTF-8 string requests.
 */
public class StringRpcServer extends RpcServer {
    public StringRpcServer(Channel channel) throws IOException
    { super(channel); }

    public StringRpcServer(Channel channel, String queueName) throws IOException
    { super(channel, queueName); }

    public static final String STRING_ENCODING = "UTF-8";

    /**
     * Overridden to do UTF-8 processing, and delegate to
     * handleStringCall. If UTF-8 is not understood by this JVM, falls
     * back to the platform default.
     */
    @SuppressWarnings("unused")
    public byte[] handleCall(byte[] requestBody, AMQP.BasicProperties replyProperties)
    {
        String request;
        try {
            request = new String(requestBody, STRING_ENCODING);
        } catch (IOException _e) {
            request = new String(requestBody);
        }
        String reply = handleStringCall(request, replyProperties);
        try {
            return reply.getBytes(STRING_ENCODING);
        } catch (IOException _e) {
            return reply.getBytes();
        }
    }

    /**
     * Delegates to handleStringCall(String).
     */
    public String handleStringCall(String request, AMQP.BasicProperties replyProperties)
    {
        return handleStringCall(request);
    }

    /**
     * Default implementation - override in subclasses. Returns the empty string.
     */
    public String handleStringCall(String request)
    {
        return "";
    }

    /**
     * Overridden to do UTF-8 processing, and delegate to
     * handleStringCast. If requestBody cannot be interpreted as UTF-8
     * tries the platform default.
     */
    public void handleCast(byte[] requestBody)
    {
        try {
            handleStringCast(new String(requestBody, STRING_ENCODING));
        } catch (IOException _e) {
            handleStringCast(new String(requestBody));
        }
    }

    /**
     * Default implementation - override in subclasses. Does nothing.
     */
    public void handleStringCast(String requestBody) {
        // Do nothing.
    }
}
