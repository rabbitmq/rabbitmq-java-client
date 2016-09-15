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

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Interface to a frame handler.
 * <h2>Concurrency</h2>
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

    void initialize(AMQConnection connection);

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
