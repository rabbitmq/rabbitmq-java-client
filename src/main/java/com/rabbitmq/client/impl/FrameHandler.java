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

package com.rabbitmq.client.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Interface to a frame handler.
 * <p/>
 * <b>Concurrency</b><br/>
 * Implementations must be thread-safe, and not allow frames to be interleaved, either while reading or writing.
 */

public interface FrameHandler extends NetworkConnection {

    /**
     * Set the underlying socket's read timeout in milliseconds, if applicable.
     * @param timeoutMs The timeout in milliseconds
     */
    void setTimeout(int timeoutMs) throws SocketException;

    /**
     * Get the underlying socket's read timeout in milliseconds.
     * @return The timeout in milliseconds
     */
    int getTimeout() throws SocketException;

    /**
     * Send the initial connection header, thus kickstarting the AMQP
     * protocol version negotiation process and putting the underlying
     * connection in a state such that the next layer of startup can
     * proceed.
     * @throws IOException if there is a problem accessing the connection
     */
    void sendHeader() throws IOException;

    /**
     * Read a {@link Frame} from the underlying data connection.
     * @return an incoming Frame, or null if there is none
     * @throws IOException if there is a problem accessing the connection
     * @throws SocketTimeoutException if the underlying read times out
     */
    Frame readFrame() throws IOException;

    /**
     * Write a {@link Frame} to the underlying data connection.
     * @param frame the Frame to transmit
     * @throws IOException if there is a problem accessing the connection
     */
    void writeFrame(Frame frame) throws IOException;

    /**
     * Flush the underlying data connection.
     * @throws IOException if there is a problem accessing the connection
     */
    void flush() throws IOException;

    /** Close the underlying data connection (complaint not permitted). */
    void close();
}
