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
import java.net.InetAddress;
import java.util.Map;

/**
 * Public API: Interface to an AMQ connection. See the see the <a href="http://www.amqp.org/">spec</a> for details.
 * <p>
 * To connect to a broker, fill in a {@link ConnectionFactory} and use a {@link ConnectionFactory} as follows:
 *
 * <pre>
 * ConnectionFactory factory = new ConnectionFactory();
 * factory.setHost(hostName);
 * factory.setPort(portNumber);
 * factory.setVirtualHost(virtualHost);
 * factory.setUsername(username);
 * factory.setPassword(password);
 * Connection conn = factory.newConnection();
 *
 * // Then open a channel:
 *
 * Channel channel = conn.createChannel();
 * </pre>
 * Or, more compactly:
 *
 * <pre>
 * ConnectionFactory factory = new ConnectionFactory();
 * factory.setUri("amqp://username:password@hostName:portNumber/virtualHost");
 * Connection conn = factory.newConnection();
 * Channel channel = conn.createChannel()
 * </pre>
 *
 * Current implementations are thread-safe for code at the client API level,
 * and in fact thread-safe internally except for code within RPC calls.
 */
public interface Connection extends ShutdownNotifier { // rename to AMQPConnection later, this is a temporary name
    /**
     * Retrieve the host.
     * @return the hostname of the peer we're connected to.
     */
    InetAddress getAddress();

    /**
     * Retrieve the port number.
     * @return the port number of the peer we're connected to.
     */

    int getPort();

    /**
     * Get the negotiated maximum channel number. Usable channel
     * numbers range from 1 to this number, inclusive.
     *
     * @return the maximum channel number permitted for this connection.
     */
    int getChannelMax();

    /**
     * Get the negotiated maximum frame size.
     *
     * @return the maximum frame size, in octets; zero if unlimited
     */
    int getFrameMax();

    /**
     * Get the negotiated heartbeat interval.
     *
     * @return the heartbeat interval, in seconds; zero if none
     */
    int getHeartbeat();

    /**
     * Get a copy of the map of client properties sent to the server
     *
     * @return a copy of the map of client properties
     */
    Map<String, Object> getClientProperties();

    /**
     * Retrieve the server properties.
     * @return a map of the server properties. This typically includes the product name and version of the server.
     */
    Map<String, Object> getServerProperties();

    /**
     * Create a new channel, using an internally allocated channel number.
     * If <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>
     * is enabled, the channel returned by this method will be {@link Recoverable}.
     *
     * @return a new channel descriptor, or null if none is available
     * @throws IOException if an I/O problem is encountered
     */
    Channel createChannel() throws IOException;

    /**
     * Create a new channel, using the specified channel number if possible.
     * @param channelNumber the channel number to allocate
     * @return a new channel descriptor, or null if this channel number is already in use
     * @throws IOException if an I/O problem is encountered
     */
    Channel createChannel(int channelNumber) throws IOException;

    /**
     * Close this connection and all its channels
     * with the {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS} close code
     * and message 'OK'.
     *
     * Waits for all the close operations to complete.
     *
     * @throws IOException if an I/O problem is encountered
     */
    void close() throws IOException;

    /**
     * Close this connection and all its channels.
     *
     * Waits for all the close operations to complete.
     *
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the connection
     * @throws IOException if an I/O problem is encountered
     */
    void close(int closeCode, String closeMessage) throws IOException;

    /**
     * Close this connection and all its channels
     * with the {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS} close code
     * and message 'OK'.
     * 
     * This method behaves in a similar way as {@link #close()}, with the only difference
     * that it waits with a provided timeout for all the close operations to
     * complete. When timeout is reached the socket is forced to close.
     * 
     * @param timeout timeout (in milliseconds) for completing all the close-related
     * operations, use -1 for infinity
     * @throws IOException if an I/O problem is encountered
     */
    void close(int timeout) throws IOException;
    
    /**
     * Close this connection and all its channels.
     *
     * Waits with the given timeout for all the close operations to complete.
     * When timeout is reached the socket is forced to close.
     * 
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the connection
     * @param timeout timeout (in milliseconds) for completing all the close-related
     * operations, use -1 for infinity
     * @throws IOException if an I/O problem is encountered
     */
    void close(int closeCode, String closeMessage, int timeout) throws IOException;

    /**
     * Abort this connection and all its channels
     * with the {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS} close code
     * and message 'OK'.
     *
     * Forces the connection to close.
     * Any encountered exceptions in the close operations are silently discarded.
     */
    void abort();
    
    /**
     * Abort this connection and all its channels.
     *
     * Forces the connection to close and waits for all the close operations to complete.
     * Any encountered exceptions in the close operations are silently discarded.
     * 
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the connection
     */
    void abort(int closeCode, String closeMessage);
    
    /**
     * Abort this connection and all its channels
     * with the {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS} close code
     * and message 'OK'.
     *
     * This method behaves in a similar way as {@link #abort()}, with the only difference
     * that it waits with a provided timeout for all the close operations to
     * complete. When timeout is reached the socket is forced to close.
     *
     * @param timeout timeout (in milliseconds) for completing all the close-related
     * operations, use -1 for infinity
     */
    void abort(int timeout);

    /**
     * Abort this connection and all its channels.
     *
     * Forces the connection to close and waits with the given timeout
     * for all the close operations to complete. When timeout is reached
     * the socket is forced to close.
     * Any encountered exceptions in the close operations are silently discarded.
     *
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the connection
     * @param timeout timeout (in milliseconds) for completing all the close-related
     * operations, use -1 for infinity
     */
    void abort(int closeCode, String closeMessage, int timeout);

    /**
     * Add a {@link BlockedListener}.
     * @param listener the listener to add
     */
    void addBlockedListener(BlockedListener listener);

    /**
     * Remove a {@link BlockedListener}.
     * @param listener the listener to remove
     * @return <code><b>true</b></code> if the listener was found and removed,
     * <code><b>false</b></code> otherwise
     */
    boolean removeBlockedListener(BlockedListener listener);

    /**
     * Remove all {@link BlockedListener}s.
     */
    void clearBlockedListeners();

    /**
     * Get the exception handler.
     *
     * @see com.rabbitmq.client.ExceptionHandler
     */
    ExceptionHandler getExceptionHandler();
}
