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
package com.rabbitmq.client;

import java.io.IOException;

/**
 * Public API: Interface to an AMQ connection. See the see the <a href="http://www.amqp.org/">spec</a> for details.
 * <p>
 * To connect to a broker, fill in a {@link ConnectionParameters} and use a {@link ConnectionFactory} as follows:
 *
 * <pre>
 * ConnectionParameters params = new ConnectionParameters();
 * params.setUsername(userName);
 * params.setPassword(password);
 * params.setVirtualHost(virtualHost);
 * params.setRequestedHeartbeat(0);
 * ConnectionFactory factory = new ConnectionFactory(params);
 * Connection conn = factory.newConnection(hostName, AMQP.PROTOCOL.PORT);
 *
 * // Then open a channel:
 *
 * Channel channel = conn.createChannel();
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
    String getHost();

    /**
     * Retrieve the port number.
     * @return the port number of the peer we're connected to.
     */

    int getPort();

    /**
     * Retrieve the connection parameters.
     * @return the initialization parameters used to open this connection.
     */
    ConnectionParameters getParameters();

    /**
     * Get the negotiated maximum number of channels allowed.
     *
     * Note that this is the <i>current</i> setting, as opposed to the <i>initially-requested</i>
     * setting available from {@link #getParameters()}.{@link ConnectionParameters#getRequestedChannelMax()}.
     *
     * @return the maximum number of simultaneously-open channels permitted for this connection.
     */
    int getChannelMax();

    /**
     * Get the negotiated maximum frame size.
     *
     * Note that this is the <i>current</i> setting, as opposed to the <i>initially-requested</i>
     * setting available from {@link #getParameters()}.{@link ConnectionParameters#getRequestedFrameMax()}.
     *
     * @return the maximum frame size, in octets; zero if unlimited
     */
    int getFrameMax();

    /**
     * Get the negotiated heartbeat interval.
     *
     * Note that this is the <i>current</i> setting, as opposed to the <i>initially-requested</i>
     * setting available from {@link #getParameters()}.{@link ConnectionParameters#getRequestedHeartbeat()}.
     *
     * @return the heartbeat interval, in seconds; zero if none
     */
    int getHeartbeat();

    /**
     * Retrieve the known hosts.
     * @return an array of addresses for all hosts that came back in the initial {@link com.rabbitmq.client.AMQP.Connection.OpenOk} open-ok method
     */
    Address[] getKnownHosts();

    /**
     * Create a new channel, using an internally allocated channel number.
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
}
