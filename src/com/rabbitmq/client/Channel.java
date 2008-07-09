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
//   The Initial Developers of the Original Code are LShift Ltd.,
//   Cohesive Financial Technologies LLC., and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd., Cohesive Financial Technologies
//   LLC., and Rabbit Technologies Ltd. are Copyright (C) 2007-2008
//   LShift Ltd., Cohesive Financial Technologies LLC., and Rabbit
//   Technologies Ltd.;
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client;

import java.io.IOException;
import java.util.Map;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Exchange;
import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.AMQP.Tx;

/**
 * Public API: Interface to an AMQ channel. See the <a href="http://www.amqp.org/">spec</a> for details.
 *  
 * <p>
 * To open a channel,
 * <pre>
 * {@link Connection} conn = ...;
 * {@link Channel} channel = conn.{@link Connection#createChannel createChannel}();
 * int ticket = channel.{@link Channel#accessRequest accessRequest}(realmName);
 * </pre>
 * <p>
 * Public API:
 * <ul>
 *  <li> getChannelNumber
 *  <li> close
 * </ul>
 * <p>
 * 
 * While a Channel can be used by multiple threads, it's important to ensure 
 * that only one thread executes a command at once. Concurrent execution of
 * commands will likely cause an UnexpectedFrameError to be thrown.
 * 
 */

public interface Channel extends ShutdownNotifier{
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
     * Close this channel with the given code and message
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the channel
     * @throws java.io.IOException if an error is encountered
     */
    void close(int closeCode, String closeMessage) throws IOException;

    /** 
     * Return the current {@link ReturnListener}.
     * @return an interface to the current return listener 
     */
    ReturnListener getReturnListener();

    /**
     * Set the current {@link ReturnListener}. 
     * @param listener the listener to use, or null indicating "don't use one".
     */
    void setReturnListener(ReturnListener listener);

    /**
     * Request a non-exclusive access ticket for the specified realm.
     * The access ticket is valid within the current channel and for the lifespan of the channel.
     * 
     * @see com.rabbitmq.client.AMQP.Access.Request
     * @param realm the name of the realm
     * @return a valid access ticket
     * @throws java.io.IOException if an error is encountered e.g. we don't have permission
     */
    int accessRequest(String realm) throws IOException;

    /**
     * Request an access ticket for the named realm and the given role and exclusivity flags
     * @see com.rabbitmq.client.AMQP.Access.Request
     * @param realm the name of the realm
     * @param exclusive true if we are requesting exclusive access
     * @param passive true if we are requesting passive access
     * @param active true if we are requesting active access
     * @param write true if we are requesting write access
     * @param read true if we are requesting read access
     * @return a valid access ticket
     * @throws java.io.IOException if an error is encountered e.g. we don't have permission
     */
    int accessRequest(String realm, boolean exclusive, boolean passive, boolean active, boolean write, boolean read) throws IOException;

    /**
     * Publish a message with both "mandatory" and "immediate" flags set to false
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the exchange to publish the message to
     * @param routingKey the routing key
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @throws java.io.IOException if an error is encountered
     */
    void basicPublish(int ticket, String exchange, String routingKey, BasicProperties props, byte[] body) throws IOException;

    /**
     * Publish a message
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the exchange to publish the message to
     * @param mandatory true if we are requesting a mandatory publish
     * @param immediate true if we are requesting an immediate publish
     * @param routingKey the routing key
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @throws java.io.IOException if an error is encountered
     */
    void basicPublish(int ticket, String exchange, String routingKey, boolean mandatory, boolean immediate, BasicProperties props, byte[] body)
            throws IOException;

    /**
     * Delete an exchange, without regard for whether it is in use or not
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the name of the exchange
     * @return a deletion-confirm method to indicate the exchange was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeleteOk exchangeDelete(int ticket, String exchange) throws IOException;

    /**
     * Actively declare a non-autodelete, non-durable exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @return a deletion-confirm method to indicate the exchange was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(int ticket, String exchange, String type) throws IOException;

    /**
     * Delete an exchange
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the name of the exchange
     * @param ifUnused true to indicate that the exchange is only to be deleted if it is unused
     * @return a deletion-confirm method to indicate the exchange was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeleteOk exchangeDelete(int ticket, String exchange, boolean ifUnused) throws IOException;

    /**
     * Actively declare a non-autodelete exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @throws java.io.IOException if an error is encountered
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     */
    Exchange.DeclareOk exchangeDeclare(int ticket, String exchange, String type, boolean durable) throws IOException;

    /**
     * Actively declare a non-exclusive, non-autodelete queue
     * The name of the new queue is held in the "queue" field of the {@link com.rabbitmq.client.AMQP.Queue.DeclareOk} result.
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeclareOk queueDeclare(int ticket, String queue, boolean durable) throws IOException;

    /**
     * Declare an exchange, via an interface that allows the complete set of arguments
     * The name of the new queue is held in the "queue" field of the {@link com.rabbitmq.client.AMQP.Queue.DeclareOk} result.
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @param exchange the name of the exchange
     * @param type the exchange type
     * @param passive true if we are passively declaring a exchange (asserting the exchange already exists)
     * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
     * @param autoDelete true if the server should delete the exchange when it is no longer in use
     * @param arguments other properties (construction arguments) for the exchange
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Exchange.DeclareOk exchangeDeclare(int ticket, String exchange, String type, boolean passive, boolean durable, boolean autoDelete,
            Map<String, Object> arguments) throws IOException;

    /**
     * Actively declare a server-named exclusive, autodelete, non-durable queue.
     * The name of the new queue is held in the "queue" field of the {@link com.rabbitmq.client.AMQP.Queue.DeclareOk} result.
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @return a declaration-confirm method to indicate the exchange was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeclareOk queueDeclare(int ticket) throws IOException;

    /**
     * Actively declare a non-exclusive, non-autodelete, non-durable queue
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @return a declaration-confirm method to indicate the queue was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeclareOk queueDeclare(int ticket, String queue) throws IOException;

    /**
     * Declare a queue
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param passive true if we are passively declaring a queue (asserting the queue already exists)
     * @param durable true if we are declaring a durable queue (the queue will survive a server restart)
     * @param exclusive true if we are declaring an exclusive queue
     * @param autoDelete true if we are declaring an autodelete queue (server will delete it when no longer in use)
     * @param arguments other properties (construction arguments) for the queue
     * @return a declaration-confirm method to indicate the queue was successfully declared
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeclareOk queueDeclare(int ticket, String queue, boolean passive, boolean durable, boolean exclusive, boolean autoDelete,
            Map<String, Object> arguments) throws IOException;

    /**
     * Delete a queue, without regard for whether it is in use or has messages on it
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @return a deletion-confirm method to indicate the queue was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeleteOk queueDelete(int ticket, String queue) throws IOException;

    /**
     * Delete a queue
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param ifUnused true if the queue should be deleted only if not in use
     * @param ifEmpty true if the queue should be deleted only if empty
     * @return a deletion-confirm method to indicate the queue was successfully deleted
     * @throws java.io.IOException if an error is encountered
     */
    Queue.DeleteOk queueDelete(int ticket, String queue, boolean ifUnused, boolean ifEmpty) throws IOException;

    /**
     * Bind a queue to an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Queue.Bind
     * @see com.rabbitmq.client.AMQP.Queue.BindOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routine key to use for the binding
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Queue.BindOk queueBind(int ticket, String queue, String exchange, String routingKey) throws IOException;

    /**
     * Bind a queue to an exchange.
     * @see com.rabbitmq.client.AMQP.Queue.Bind
     * @see com.rabbitmq.client.AMQP.Queue.BindOk
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param exchange the name of the exchange
     * @param routingKey the routine key to use for the binding
     * @param arguments other properties (binding parameters)
     * @return a binding-confirm method if the binding was successfully created
     * @throws java.io.IOException if an error is encountered
     */
    Queue.BindOk queueBind(int ticket, String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;

    /**
     * Retrieve a message from a queue using {@link com.rabbitmq.client.AMQP.Basic.Get}
     * @see com.rabbitmq.client.AMQP.Basic.Get
     * @see com.rabbitmq.client.AMQP.Basic.GetOk
     * @see com.rabbitmq.client.AMQP.Basic.GetEmpty
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param noAck true if no handshake is required
     * @return a {@link GetResponse} containing the retrieved message data
     * @throws java.io.IOException if an error is encountered
     */
    GetResponse basicGet(int ticket, String queue, boolean noAck) throws IOException;

    /**
     * Acknowledge one or several received
     * messages. Supply the deliveryTag from the {@link com.rabbitmq.client.AMQP.Basic.GetOk}
     * or {@link com.rabbitmq.client.AMQP.Basic.Deliver} method
     * containing the received message being acknowledged.
     * @see com.rabbitmq.client.AMQP.Basic.Ack
     * @param deliveryTag the tag from the received {@link com.rabbitmq.client.AMQP.Basic.GetOk} or {@link com.rabbitmq.client.AMQP.Basic.Deliver}
     * @param multiple true if we are acknowledging multiple messages with the same delivery tag 
     * @throws java.io.IOException if an error is encountered
     */
    void basicAck(long deliveryTag, boolean multiple) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * explicit acknowledgements required and a server-generated consumerTag.
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param callback an interface to the consumer object
     * @return the consumerTag generated by the server
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see #basicConsume(int,String,boolean,String,boolean,boolean,Consumer)
     */
    String basicConsume(int ticket, String queue, Consumer callback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer, with
     * a server-generated consumerTag.
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param noAck true if no handshake is required
     * @param callback an interface to the consumer object
     * @return the consumerTag generated by the server
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(int,String,boolean,String,boolean,boolean,Consumer)
     */
    String basicConsume(int ticket, String queue, boolean noAck, Consumer callback) throws IOException;

    /**
     * Start a non-nolocal, non-exclusive consumer.
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param noAck true if no handshake is required
     * @param consumerTag a client-generated consumer tag to establish context
     * @param callback an interface to the consumer object
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicConsume(int,String,boolean,String,boolean,boolean,Consumer)
     */
    String basicConsume(int ticket, String queue, boolean noAck, String consumerTag, Consumer callback) throws IOException;

    /**
     * Start a consumer. Calls the consumer's {@link Consumer#handleConsumeOk}
     * method before returning.
     * @param ticket an access ticket for the appropriate realm
     * @param queue the name of the queue
     * @param noAck true if no handshake is required
     * @param consumerTag a client-generated consumer tag to establish context
     * @param noLocal flag set to true unless server local buffering is required
     * @param exclusive true if this is an exclusive consumer
     * @param callback an interface to the consumer object
     * @return the consumerTag associated with the new consumer
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     */
    String basicConsume(int ticket, String queue, boolean noAck, String consumerTag, boolean noLocal, boolean exclusive, Consumer callback) throws IOException;

    /**
     * Cancel a consumer. Calls the consumer's {@link Consumer#handleCancelOk}
     * method before returning.
     * @param consumerTag a client- or server-generated consumer tag to establish context
     * @throws java.io.IOException if an error is encountered
     * @see com.rabbitmq.client.AMQP.Basic.Cancel
     * @see com.rabbitmq.client.AMQP.Basic.CancelOk
     */
    void basicCancel(String consumerTag) throws IOException;

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
}
