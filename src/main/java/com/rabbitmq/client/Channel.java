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

package com.rabbitmq.client;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Interface to a channel. All non-deprecated methods of
 * this interface are part of the public API.
 *
 * <h2>Tutorials</h2>
 * <a href="https://www.rabbitmq.com/getstarted.html">RabbitMQ tutorials</a> demonstrate how
 * key methods of this interface are used.
 *
 * <h2>User Guide</h2>
 * See <a href="https://www.rabbitmq.com/api-guide.html">Java Client User Guide</a>.
 *
 * <h2>Concurrency Considerations</h2>
 * <p>
 * {@link Channel} instances must not be shared between
 * threads. Applications
 * should prefer using a {@link Channel} per thread
 * instead of sharing the same {@link Channel} across
 * multiple threads. While some operations on channels are safe to invoke
 * concurrently, some are not and will result in incorrect frame interleaving
 * on the wire. Sharing channels between threads will also interfere with
 * <a href="https://www.rabbitmq.com/confirms.html">Publisher Confirms</a>.
 *
 * As such, applications need to use a {@link Channel} per thread.
 * </p>
 *
 * @see <a href="https://www.rabbitmq.com/getstarted.html">RabbitMQ tutorials</a>
 * @see <a href="https://www.rabbitmq.com/api-guide.html">RabbitMQ Java Client User Guide</a>
 */
public interface Channel extends ShutdownNotifier, AutoCloseable {
    /**
     * Retrieve this channel's channel number.
     * @return the channel number
     */
    int getChannelNumber();

    /**
     * Retrieve the connection which carries this channel.
     * @return the underlying {@link Connection}
     */
    Connection getConnection();

    /**
     * Close this channel with the {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS} close code
     * and message 'OK'.
     *
     * @throws java.io.IOException if an error is encountered
     */
    @Override
    void close() throws IOException, TimeoutException;

    /**
     * Close this channel.
     *
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the connection
     * @throws java.io.IOException if an error is encountered
     */
    void close(int closeCode, String closeMessage) throws IOException, TimeoutException;

    /**
     * Abort this channel with the {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS} close code
     * and message 'OK'.
     *
     * Forces the channel to close and waits for the close operation to complete.
     * Any encountered exceptions in the close operation are silently discarded.
     */
    void abort() throws IOException;

    /**
     * Abort this channel.
     *
     * Forces the channel to close and waits for the close operation to complete.
     * Any encountered exceptions in the close operation are silently discarded.
     */
    void abort(int closeCode, String closeMessage) throws IOException;

    /**
     * Add a {@link ReturnListener}.
     * @param listener the listener to add
     */
    void addReturnListener(ReturnListener listener);

    /**
     * Add a lambda-based {@link ReturnListener}.
     * @see ReturnListener
     * @see ReturnCallback
     * @see Return
     * @param returnCallback the callback when the message is returned
     * @return the listener that wraps the callback
     */
    ReturnListener addReturnListener(ReturnCallback returnCallback);

    /**
     * Remove a {@link ReturnListener}.
     * @param listener the listener to remove
     * @return <code><b>true</b></code> if the listener was found and removed,
     * <code><b>false</b></code> otherwise
     */
    boolean removeReturnListener(ReturnListener listener);

    /**
     * Remove all {@link ReturnListener}s.
     */
    void clearReturnListeners();

    /**
     * Add a {@link ConfirmListener}.
     * @param listener the listener to add
     */
    void addConfirmListener(ConfirmListener listener);

    /**
     * Add a lambda-based {@link ConfirmListener}.
     * @see ConfirmListener
     * @see ConfirmCallback
     * @param ackCallback callback on ack
     * @param nackCallback call on nack (negative ack)
     * @return the listener that wraps the callbacks
     */
    ConfirmListener addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback);

    /**
     * Remove a {@link ConfirmListener}.
     * @param listener the listener to remove
     * @return <code><b>true</b></code> if the listener was found and removed,
     * <code><b>false</b></code> otherwise
     */
    boolean removeConfirmListener(ConfirmListener listener);

    /**
     * Remove all {@link ConfirmListener}s.
     */
    void clearConfirmListeners();

    /**
     * Get the current default consumer. @see setDefaultConsumer for rationale.
     * @return an interface to the current default consumer.
     */
    Consumer getDefaultConsumer();

    /**
     * Set the current default consumer.
     *
     * Under certain circumstances it is possible for a channel to receive a
     * message delivery which does not match any consumer which is currently
     * set up via basicConsume(). This will occur after the following sequence
     * of events:
     *
     * ctag = basicConsume(queue, consumer); // i.e. with explicit acks
     * // some deliveries take place but are not acked
     * basicCancel(ctag);
     * basicRecover(false);
     *
     * Since requeue is specified to be false in the basicRecover, the spec
     * states that the message must be redelivered to "the original recipient"
     * - i.e. the same channel / consumer-tag. But the consumer is no longer
     * active.
     *
     * In these circumstances, you can register a default consumer to handle
     * such deliveries. If no default consumer is registered an
     * IllegalStateException will be thrown when such a delivery arrives.
     *
     * Most people will not need to use this.
     *
     * @param consumer the consumer to use, or null indicating "don't use one".
     */
    void setDefaultConsumer(Consumer consumer);

    /**
     * Request specific "quality of service" settings.
     *
     * These settings impose limits on the amount of data the server
     * will deliver to consumers before requiring acknowledgements.
     * Thus they provide a means of consumer-initiated flow control.
     * @see com.rabbitmq.client.AMQP.Basic.Qos
     * @param prefetchSize maximum amount of content (measured in
     * octets) that the server will deliver, 0 if unlimited
     * @param prefetchCount maximum number of messages that the server
     * will deliver, 0 if unlimited
     * @param global true if the settings should be applied to the
     * entire channel rather than each consumer
     * @throws java.io.IOException if an error is encountered
     */
    void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException;

    /**
     * Request a specific prefetchCount "quality of service" settings
     * for this channel.
     *
     * @see #basicQos(int, int, boolean)
     * @param prefetchCount maximum number of messages that the server
     * will deliver, 0 if unlimited
     * @param global true if the settings should be applied to the
     * entire channel rather than each consumer
     * @throws java.io.IOException if an error is encountered
     */
    void basicQos(int prefetchCount, boolean global) throws IOException;

    /**
     * Request a specific prefetchCount "quality of service" settings
     * for this channel.
     *
     * @see #basicQos(int, int, boolean)
     * @param prefetchCount maximum number of messages that the server
     * will deliver, 0 if unlimited
     * @throws java.io.IOException if an error is encountered
     */
    void basicQos(int prefetchCount) throws IOException;

    /**
     * Publish a message.
     *
     * Publishing to a non-existent exchange will result in a channel-level
     * protocol exception, which closes the channel.
     *
     * Invocations of <code>Channel#basicPublish</code> will eventually block if a
     * <a href="https://www.rabbitmq.com/alarms.html">resource-driven alarm</a> is in effect.
     *
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     * @see <a href="https://www.rabbitmq.com/alarms.html">Resource-driven alarms</a>
     * @param exchange the exchange to publish the message to
     * @param routingKey the routing key
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @throws java.io.IOException if an error is encountered
     */
    void basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body) throws IOException;

    /**
     * Publish a message.
     *
     * Invocations of <code>Channel#basicPublish</code> will eventually block if a
     * <a href="https://www.rabbitmq.com/alarms.html">resource-driven alarm</a> is in effect.
     *
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     * @see <a href="https://www.rabbitmq.com/alarms.html">Resource-driven alarms</a>
     * @param exchange the exchange to publish the message to
     * @param routingKey the routing key
     * @param mandatory true if the 'mandatory' flag is to be set
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @throws java.io.IOException if an error is encountered
     */
    void basicPublish(String exchange, String routingKey, boolean mandatory, BasicProperties props, byte[] body)
            throws IOException;

    /**
     * Publish a message.
     *
     * Publishing to a non-existent exchange will result in a channel-level
     * protocol exception, which closes the channel.
     *
     * Invocations of <code>Channel#basicPublish</code> will eventually block if a
     * <a href="https://www.rabbitmq.com/alarms.html">resource-driven alarm</a> is in effect.
     *
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     * @see <a href="https://www.rabbitmq.com/alarms.html">Resource-driven alarms</a>
     * @param exchange the exchange to publish the message to
     * @param routingKey the routing key
     * @param mandatory true if the 'mandatory' flag is to be set
     * @param immediate true if the 'immediate' flag is to be
     * set. Note that the RabbitMQ server does not support this flag.
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @throws java.io.IOException if an error is encountered
     */
    void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate, BasicProperties props, byte[] body)
            throws IOException;

    /**
     * Actively declare a non-autodelete, non-durable exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException;

    /**
     * Actively declare a non-autodelete, non-durable exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type) throws IOException;

    /**
     * Actively declare a non-autodelete exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @throws java.io.IOException if an error is encountered
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     */
    Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException;

    /**
     * Actively declare a non-autodelete exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @throws java.io.IOException if an error is encountered
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     */
    Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) throws IOException;

    /**
     * Declare an exchange.
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param arguments other properties (construction arguments) for the exchange
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete,
                                       Map<String, Object> arguments) throws IOException;

    /**
     * Declare an exchange.
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param arguments other properties (construction arguments) for the exchange
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete,
        Map<String, Object> arguments) throws IOException;

    /**
     * Declare an exchange, via an interface that allows the complete set of
     * arguments.
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param internal true if the exchange is internal, i.e. can't be directly
     * published to by a client.
     * @param arguments other properties (construction arguments) for the exchange
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(String exchange,
                                              String type,
                                              boolean durable,
                                              boolean autoDelete,
                                              boolean internal,
                                              Map<String, Object> arguments) throws IOException;

    /**
     * Declare an exchange, via an interface that allows the complete set of
     * arguments.
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param internal true if the exchange is internal, i.e. can't be directly
     * published to by a client.
     * @param arguments other properties (construction arguments) for the exchange
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(String exchange,
        BuiltinExchangeType type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        Map<String, Object> arguments) throws IOException;

    /**
     * Like {@link Channel#exchangeDeclare(String, String, boolean, boolean, java.util.Map)} but
     * sets nowait parameter to true and returns nothing (as there will be no response from
     * the server).
     *
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param internal true if the exchange is internal, i.e. can't be directly
     * published to by a client.
     * @param arguments other properties (construction arguments) for the exchange
     * @throws java.io.IOException if an error is encountered
     */
    void exchangeDeclareNoWait(String exchange,
                               String type,
                               boolean durable,
                               boolean autoDelete,
                               boolean internal,
                               Map<String, Object> arguments) throws IOException;

    /**
     * Like {@link Channel#exchangeDeclare(String, String, boolean, boolean, java.util.Map)} but
     * sets nowait parameter to true and returns nothing (as there will be no response from
     * the server).
     *
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param internal true if the exchange is internal, i.e. can't be directly
     * published to by a client.
     * @param arguments other properties (construction arguments) for the exchange
     * @throws java.io.IOException if an error is encountered
     */
    void exchangeDeclareNoWait(String exchange,
        BuiltinExchangeType type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        Map<String, Object> arguments) throws IOException;

    /**
     * Declare an exchange passively; that is, check if the named exchange exists.
     * @param name check the existence of an exchange named this
     * @throws IOException the server will raise a 404 channel exception if the named exchange does not exist.
     */
    Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException;

    /**
     * Delete an exchange
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     * @param exchange the name of the exchange
     * @param ifUnused true to indicate that the exchange is only to be deleted if it is unused
     * @return a deletion-confirm method to indicate the exchange was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException;

    /**
     * Like {@link Channel#exchangeDelete(String, boolean)} but sets nowait parameter to true
     * and returns void (as there will be no response from the server).
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     * @param exchange the name of the exchange
     * @param ifUnused true to indicate that the exchange is only to be deleted if it is unused
     * @throws java.io.IOException if an error is encountered
     */
    void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException;


    /**
     * Delete an exchange, without regard for whether it is in use or not
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     * @param exchange the name of the exchange
     * @return a deletion-confirm method to indicate the exchange was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeleteOk exchangeDelete(String exchange) throws IOException;

    /**
     * Bind an exchange to an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Exchange.Bind
     * @see com.rabbitmq.client.AMQP.Exchange.BindOk
     * @param destination the name of the exchange to which messages flow across the binding
     * @param source the name of the exchange from which messages flow across the binding
     * @param routingKey the routing key to use for the binding
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException;

    /**
     * Bind an exchange to an exchange.
     * @see com.rabbitmq.client.AMQP.Exchange.Bind
     * @see com.rabbitmq.client.AMQP.Exchange.BindOk
     * @param destination the name of the exchange to which messages flow across the binding
     * @param source the name of the exchange from which messages flow across the binding
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.BindOk exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Like {@link Channel#exchangeBind(String, String, String, java.util.Map)} but sets nowait parameter
     * to true and returns void (as there will be no response from the server).
     * @param destination the name of the exchange to which messages flow across the binding
     * @param source the name of the exchange from which messages flow across the binding
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @throws java.io.IOException if an error is encountered
     */
    void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Unbind an exchange from an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Exchange.Bind
     * @see com.rabbitmq.client.AMQP.Exchange.BindOk
     * @param destination the name of the exchange to which messages flow across the binding
     * @param source the name of the exchange from which messages flow across the binding
     * @param routingKey the routing key to use for the binding
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey) throws IOException;

    /**
     * Unbind an exchange from an exchange.
     * @see com.rabbitmq.client.AMQP.Exchange.Bind
     * @see com.rabbitmq.client.AMQP.Exchange.BindOk
     * @param destination the name of the exchange to which messages flow across the binding
     * @param source the name of the exchange from which messages flow across the binding
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Same as {@link Channel#exchangeUnbind(String, String, String, java.util.Map)} but sets no-wait parameter to true
     * and returns nothing (as there will be no response from the server).
     * @param destination the name of the exchange to which messages flow across the binding
     * @param source the name of the exchange from which messages flow across the binding
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @throws java.io.IOException if an error is encountered
     */
    void exchangeUnbindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Actively declare a server-named exclusive, autodelete, non-durable queue.
     * The name of the new queue is held in the "queue" field of the {@link com.rabbitmq.client.AMQP.Queue.DeclareOk} result.
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @return a declaration-confirm method to indicate the queue was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeclareOk queueDeclare() throws IOException;

    /**
     * Declare a queue
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @param queue the name of the queue
     * @param durable true if we are declaring a durable queue (the queue will survive a server restart)
     * @param exclusive true if we are declaring an exclusive queue (restricted to this connection)
     * @param autoDelete true if we are declaring an autodelete queue (server will delete it when no longer in use)
     * @param arguments other properties (construction arguments) for the queue
     * @return a declaration-confirm method to indicate the queue was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete,
                                 Map<String, Object> arguments) throws IOException;

    /**
     * Like {@link Channel#queueDeclare(String, boolean, boolean, boolean, java.util.Map)} but sets nowait
     * flag to true and returns no result (as there will be no response from the server).
     * @param queue the name of the queue
     * @param durable true if we are declaring a durable queue (the queue will survive a server restart)
     * @param exclusive true if we are declaring an exclusive queue (restricted to this connection)
     * @param autoDelete true if we are declaring an autodelete queue (server will delete it when no longer in use)
     * @param arguments other properties (construction arguments) for the queue
     * @throws java.io.IOException if an error is encountered
     */
    void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete,
                            Map<String, Object> arguments) throws IOException;

    /**
     * Declare a queue passively; i.e., check if it exists.  In AMQP
     * 0-9-1, all arguments aside from nowait are ignored; and sending
     * nowait makes this method a no-op, so we default it to false.
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @param queue the name of the queue
     * @return a declaration-confirm method to indicate the queue exists
     * @throws java.io.IOException if an error is encountered,
     * including if the queue does not exist and if the queue is
     * exclusively owned by another connection.
     */
    Queue.DeclareOk queueDeclarePassive(String queue) throws IOException;

    /**
     * Delete a queue, without regard for whether it is in use or has messages on it
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     * @param queue the name of the queue
     * @return a deletion-confirm method to indicate the queue was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeleteOk queueDelete(String queue) throws IOException;

    /**
     * Delete a queue
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     * @param queue the name of the queue
     * @param ifUnused true if the queue should be deleted only if not in use
     * @param ifEmpty true if the queue should be deleted only if empty
     * @return a deletion-confirm method to indicate the queue was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException;

    /**
     * Like {@link Channel#queueDelete(String, boolean, boolean)} but sets nowait parameter
     * to true and returns nothing (as there will be no response from the server).
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     * @param queue the name of the queue
     * @param ifUnused true if the queue should be deleted only if not in use
     * @param ifEmpty true if the queue should be deleted only if empty
     * @throws java.io.IOException if an error is encountered
     */
    void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException;

    /**
     * Bind a queue to an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Queue.Bind
     * @see com.rabbitmq.client.AMQP.Queue.BindOk
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routing key to use for the binding
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException;

    /**
     * Bind a queue to an exchange.
     * @see com.rabbitmq.client.AMQP.Queue.Bind
     * @see com.rabbitmq.client.AMQP.Queue.BindOk
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Same as {@link Channel#queueBind(String, String, String, java.util.Map)} but sets nowait
     * parameter to true and returns void (as there will be no response
     * from the server).
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @throws java.io.IOException if an error is encountered
     */
    void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Unbinds a queue from an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Queue.Unbind
     * @see com.rabbitmq.client.AMQP.Queue.UnbindOk
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routing key to use for the binding
     * @return an unbinding-confirm method if the binding was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException;

    /**
     * Unbind a queue from an exchange.
     * @see com.rabbitmq.client.AMQP.Queue.Unbind
     * @see com.rabbitmq.client.AMQP.Queue.UnbindOk
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routing key to use for the binding
     * @param arguments other properties (binding parameters)
     * @return an unbinding-confirm method if the binding was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Purges the contents of the given queue.
     * @see com.rabbitmq.client.AMQP.Queue.Purge
     * @see com.rabbitmq.client.AMQP.Queue.PurgeOk
     * @param queue the name of the queue
     * @return a purge-confirm method if the purge was executed successfully
     * @throws java.io.IOException if an error is encountered
     */
    Queue.PurgeOk queuePurge(String queue) throws IOException;

    /**
     * Retrieve a message from a queue using {@link com.rabbitmq.client.AMQP.Basic.Get}
     * @see com.rabbitmq.client.AMQP.Basic.Get
     * @see com.rabbitmq.client.AMQP.Basic.GetOk
     * @see com.rabbitmq.client.AMQP.Basic.GetEmpty
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @return a {@link GetResponse} containing the retrieved message data
     * @throws java.io.IOException if an error is encountered
     */
    GetResponse basicGet(String queue, boolean autoAck) throws IOException;

    /**
     * Acknowledge one or several received
     * messages. Supply the deliveryTag from the {@link com.rabbitmq.client.AMQP.Basic.GetOk}
     * or {@link com.rabbitmq.client.AMQP.Basic.Deliver} method
     * containing the received message being acknowledged.
     * @see com.rabbitmq.client.AMQP.Basic.Ack
     * @param deliveryTag the tag from the received {@link com.rabbitmq.client.AMQP.Basic.GetOk} or {@link com.rabbitmq.client.AMQP.Basic.Deliver}
     * @param multiple true to acknowledge all messages up to and
     * including the supplied delivery tag; false to acknowledge just
     * the supplied delivery tag.
     * @throws java.io.IOException if an error is encountered
     */
    void basicAck(long deliveryTag, boolean multiple) throws IOException;

    /**
     * Reject one or several received messages.
     *
     * Supply the <code>deliveryTag</code> from the {@link com.rabbitmq.client.AMQP.Basic.GetOk}
     * or {@link com.rabbitmq.client.AMQP.Basic.GetOk} method containing the message to be rejected.
     * @see com.rabbitmq.client.AMQP.Basic.Nack
     * @param deliveryTag the tag from the received {@link com.rabbitmq.client.AMQP.Basic.GetOk} or {@link com.rabbitmq.client.AMQP.Basic.Deliver}
     * @param multiple true to reject all messages up to and including
     * the supplied delivery tag; false to reject just the supplied
     * delivery tag.
     * @param requeue true if the rejected message(s) should be requeued rather
     * than discarded/dead-lettered
     * @throws java.io.IOException if an error is encountered
     */
    void basicNack(long deliveryTag, boolean multiple, boolean requeue)
            throws IOException;

    /**
     * Reject a message. Supply the deliveryTag from the {@link com.rabbitmq.client.AMQP.Basic.GetOk}
     * or {@link com.rabbitmq.client.AMQP.Basic.Deliver} method
     * containing the received message being rejected.
     * @see com.rabbitmq.client.AMQP.Basic.Reject
     * @param deliveryTag the tag from the received {@link com.rabbitmq.client.AMQP.Basic.GetOk} or {@link com.rabbitmq.client.AMQP.Basic.Deliver}
     * @param requeue true if the rejected message should be requeued rather than discarded/dead-lettered
     * @throws java.io.IOException if an error is encountered
     */
    void basicReject(long deliveryTag, boolean requeue) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * explicit acknowledgement and a server-generated consumerTag.
     * @param queue the name of the queue
     * @param callback an interface to the consumer object
     * @return the consumerTag generated by the server
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     */
    String basicConsume(String queue, Consumer callback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * explicit acknowledgement and a server-generated consumerTag.
     * Provide access only to <code>basic.deliver</code> and
     * <code>basic.cancel</code> AMQP methods (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * explicit acknowledgement and a server-generated consumerTag.
     * Provide access only to <code>basic.deliver</code> and
     * shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param deliverCallback callback when a message is delivered
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * explicit acknowledgement and a server-generated consumerTag.
     * Provide access to <code>basic.deliver</code>, <code>basic.cancel</code>
     * and shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param callback an interface to the consumer object
     * @return the consumerTag generated by the server
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     */
    String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag.
     * Provide access only to <code>basic.deliver</code> and
     * <code>basic.cancel</code> AMQP methods (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag.
     * Provide access only to <code>basic.deliver</code> and
     * shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param deliverCallback callback when a message is delivered
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag.
     * Provide access to <code>basic.deliver</code>, <code>basic.cancel</code>
     * and shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag and specified arguments.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param arguments a set of arguments for the consume
     * @param callback an interface to the consumer object
     * @return the consumerTag generated by the server
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     */
    String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag and specified arguments.
     * Provide access only to <code>basic.deliver</code> and
     * <code>basic.cancel</code> AMQP methods (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param arguments a set of arguments for the consume
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag and specified arguments.
     * Provide access only to <code>basic.deliver</code> and
     * shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param arguments a set of arguments for the consume
     * @param deliverCallback callback when a message is delivered
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag and specified arguments.
     * Provide access to <code>basic.deliver</code>, <code>basic.cancel</code>
     * and shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param arguments a set of arguments for the consume
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag generated by the server
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param callback an interface to the consumer object
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer.
     * Provide access only to <code>basic.deliver</code> and
     * <code>basic.cancel</code> AMQP methods (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer.
     * Provide access only to <code>basic.deliver</code> and
     * shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param deliverCallback callback when a message is delivered
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer.
     * Provide access to <code>basic.deliver</code>, <code>basic.cancel</code>
     * and shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(String, boolean, String, boolean, boolean, Map, Consumer)
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a consumer. Calls the consumer's {@link Consumer#handleConsumeOk}
     * method.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param noLocal True if the server should not deliver to this consumer
     * messages published on this channel's connection. Note that the RabbitMQ server does not support this flag.
     * @param exclusive true if this is an exclusive consumer
     * @param callback an interface to the consumer object
     * @param arguments a set of arguments for the consume
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException;

    /**
     * Start a consumer. Calls the consumer's {@link Consumer#handleConsumeOk}
     * method.
     * Provide access only to <code>basic.deliver</code> and
     * <code>basic.cancel</code> AMQP methods (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param noLocal True if the server should not deliver to this consumer
     * messages published on this channel's connection. Note that the RabbitMQ server does not support this flag.
     * @param exclusive true if this is an exclusive consumer
     * @param arguments a set of arguments for the consume
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException;

    /**
     * Start a consumer. Calls the consumer's {@link Consumer#handleConsumeOk}
     * method.
     * Provide access only to <code>basic.deliver</code> and
     * shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param noLocal True if the server should not deliver to this consumer
     * messages published on this channel's connection. Note that the RabbitMQ server does not support this flag.
     * @param exclusive true if this is an exclusive consumer
     * @param arguments a set of arguments for the consume
     * @param deliverCallback callback when a message is delivered
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Start a consumer. Calls the consumer's {@link Consumer#handleConsumeOk}
     * method.
     * Provide access to <code>basic.deliver</code>, <code>basic.cancel</code>
     * and shutdown signal callbacks (which is sufficient
     * for most cases). See methods with a {@link Consumer} argument
     * to have access to all the application callbacks.
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages
     * acknowledged once delivered; false if the server should expect
     * explicit acknowledgements
     * @param consumerTag a client-generated consumer tag to establish context
     * @param noLocal True if the server should not deliver to this consumer
     * messages published on this channel's connection. Note that the RabbitMQ server does not support this flag.
     * @param exclusive true if this is an exclusive consumer
     * @param arguments a set of arguments for the consume
     * @param deliverCallback callback when a message is delivered
     * @param cancelCallback callback when the consumer is cancelled
     * @param shutdownSignalCallback callback when the channel/connection is shut down
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @since 5.0
     */
    String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException;

    /**
     * Cancel a consumer. Calls the consumer's {@link Consumer#handleCancelOk}
     * method.
     * <p>
     * A consumer tag that does not match any consumer is ignored.
     *
     * @param consumerTag a client- or server-generated consumer tag to establish context
     * @throws IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Cancel
     * @see com.rabbitmq.client.AMQP.Basic.CancelOk
     */
    void basicCancel(String consumerTag) throws IOException;

    /**
     * <p>
     *  Ask the broker to resend unacknowledged messages.  In 0-8
     * basic.recover is asynchronous; in 0-9-1 it is synchronous, and
     * the new, deprecated method basic.recover_async is asynchronous.
     * </p>
     * Equivalent to calling <code>basicRecover(true)</code>, messages
     * will be requeued and possibly delivered to a different consumer.
     * @see #basicRecover(boolean)
     */
     Basic.RecoverOk basicRecover() throws IOException;

    /**
     * Ask the broker to resend unacknowledged messages.  In 0-8
     * basic.recover is asynchronous; in 0-9-1 it is synchronous, and
     * the new, deprecated method basic.recover_async is asynchronous.
     * @param requeue If true, messages will be requeued and possibly
     * delivered to a different consumer. If false, messages will be
     * redelivered to the same consumer.
     */
    Basic.RecoverOk basicRecover(boolean requeue) throws IOException;

    /**
     * Enables TX mode on this channel.
     * @see com.rabbitmq.client.AMQP.Tx.Select
     * @see com.rabbitmq.client.AMQP.Tx.SelectOk
     * @return a transaction-selection method to indicate the transaction was successfully initiated
     * @throws java.io.IOException if an error is encountered
     */
    Tx.SelectOk txSelect() throws IOException;

    /**
     * Commits a TX transaction on this channel.
     * @see com.rabbitmq.client.AMQP.Tx.Commit
     * @see com.rabbitmq.client.AMQP.Tx.CommitOk
     * @return a transaction-commit method to indicate the transaction was successfully committed
     * @throws java.io.IOException if an error is encountered
     */
    Tx.CommitOk txCommit() throws IOException;

    /**
     * Rolls back a TX transaction on this channel.
     * @see com.rabbitmq.client.AMQP.Tx.Rollback
     * @see com.rabbitmq.client.AMQP.Tx.RollbackOk
     * @return a transaction-rollback method to indicate the transaction was successfully rolled back
     * @throws java.io.IOException if an error is encountered
     */
    Tx.RollbackOk txRollback() throws IOException;

    /**
     * Enables publisher acknowledgements on this channel.
     * @see com.rabbitmq.client.AMQP.Confirm.Select
     * @throws java.io.IOException if an error is encountered
     */
    Confirm.SelectOk confirmSelect() throws IOException;

    /**
     * When in confirm mode, returns the sequence number of the next
     * message to be published.
     * @return the sequence number of the next message to be published
     */
    long getNextPublishSeqNo();

    /**
     * Wait until all messages published since the last call have been
     * either ack'd or nack'd by the broker.  Note, when called on a
     * non-Confirm channel, waitForConfirms throws an IllegalStateException.
     * @return whether all the messages were ack'd (and none were nack'd)
     * @throws java.lang.IllegalStateException
     */
    boolean waitForConfirms() throws InterruptedException;

    /**
     * Wait until all messages published since the last call have been
     * either ack'd or nack'd by the broker; or until timeout elapses.
     * If the timeout expires a TimeoutException is thrown.  When
     * called on a non-Confirm channel, waitForConfirms throws an
     * IllegalStateException.
     * @return whether all the messages were ack'd (and none were nack'd)
     * @throws java.lang.IllegalStateException
     */
    boolean waitForConfirms(long timeout) throws InterruptedException, TimeoutException;

    /** Wait until all messages published since the last call have
     * been either ack'd or nack'd by the broker.  If any of the
     * messages were nack'd, waitForConfirmsOrDie will throw an
     * IOException.  When called on a non-Confirm channel, it will
     * throw an IllegalStateException.
     * @throws java.lang.IllegalStateException
     */
     void waitForConfirmsOrDie() throws IOException, InterruptedException;

    /** Wait until all messages published since the last call have
     * been either ack'd or nack'd by the broker; or until timeout elapses.
     * If the timeout expires a TimeoutException is thrown.  If any of the
     * messages were nack'd, waitForConfirmsOrDie will throw an
     * IOException.  When called on a non-Confirm channel, it will
     * throw an IllegalStateException.
     * @throws java.lang.IllegalStateException
     */
    void waitForConfirmsOrDie(long timeout) throws IOException, InterruptedException, TimeoutException;

    /**
     * Asynchronously send a method over this channel.
     * @param method method to transmit over this channel.
     * @throws IOException Problem transmitting method.
     */
    void asyncRpc(Method method) throws IOException;

    /**
     * Synchronously send a method over this channel.
     * @param method method to transmit over this channel.
     * @return command response to method. Caller should cast as appropriate.
     * @throws IOException Problem transmitting method.
     */
    Command rpc(Method method) throws IOException;

    /**
     * Returns the number of messages in a queue ready to be delivered
     * to consumers. This method assumes the queue exists. If it doesn't,
     * the channels will be closed with an exception.
     * @param queue the name of the queue
     * @return the number of messages in ready state
     * @throws IOException Problem transmitting method.
     */
    long messageCount(String queue) throws IOException;

    /**
     * Returns the number of consumers on a queue.
     * This method assumes the queue exists. If it doesn't,
     * the channel will be closed with an exception.
     * @param queue the name of the queue
     * @return the number of consumers
     * @throws IOException Problem transmitting method.
     */
    long consumerCount(String queue) throws IOException;

    /**
     * Asynchronously send a method over this channel.
     * @param method method to transmit over this channel.
     * @return a completable future that completes when the result is received
     * @throws IOException Problem transmitting method.
     */
    CompletableFuture<Command> asyncCompletableRpc(Method method) throws IOException;

}
