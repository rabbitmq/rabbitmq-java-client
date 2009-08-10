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
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Interface to a frame handler.
 */

public interface FrameHandler {
    /** Retrieve hostname of peer. */
    public String getHost();

    /** Retrieve port number of peer. */
    public int getPort();

    /**
     * Set the underlying socket's read timeout in milliseconds, if applicable.
     *
     * @param timeoutMs
     *            The timeout in milliseconds
     */
    void setTimeout(int timeoutMs) throws SocketException;

    /**
     * Get the underlying socket's timeout in milliseconds.
     *
     * @return The timeout in milliseconds
     */
    int getTimeout() throws SocketException;

    /**
     * Send the initial connection header, thus kickstarting the AMQP
     * protocol version negotiation process and putting the underlying
     * connection in a state such that the next layer of startup can
     * proceed.
     */
    void sendHeader() throws IOException;

    /**
     * Read a {@link Frame} from the underlying data connection.
     *
     * @return an incoming Frame, or null if there is none
     * @throws IOException if there is a problem accessing the connection
     * @throws SocketTimeoutException if the underlying read times out
     */
    Frame readFrame() throws IOException;

    /**
     * Write a {@link Frame} to the underlying data connection.
     *
     * @param frame the Frame to transmit
     * @throws IOException if there is a problem accessing the connection
     */
    void writeFrame(Frame frame) throws IOException;

    /**
     * Close the underlying data connection (complaint not permitted).
     */
    void close();
}
