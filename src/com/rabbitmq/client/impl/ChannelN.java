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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.UnexpectedMethodError;
import com.rabbitmq.client.impl.AMQImpl.Basic;
import com.rabbitmq.client.impl.AMQImpl.Channel;
import com.rabbitmq.client.impl.AMQImpl.Exchange;
import com.rabbitmq.client.impl.AMQImpl.Queue;
import com.rabbitmq.client.impl.AMQImpl.Tx;
import com.rabbitmq.utility.Utility;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;


/**
 * Main interface to AMQP protocol functionality. Public API -
 * Implementation of all AMQChannels except channel zero.
 * <p>
 * To open a channel,
 * <pre>
 * {@link Connection} conn = ...;
 * {@link ChannelN} ch1 = conn.{@link Connection#createChannel createChannel}(1);
 * ch1.{@link ChannelN#open open}("");
 * </pre>
 */
public class ChannelN extends AMQChannel implements com.rabbitmq.client.Channel {
    private static final String UNSPECIFIED_OUT_OF_BAND = "";

    /**
     * When 0.9.1 is signed off, tickets can be removed from the codec
     * and this field can be deleted.
     */
    @Deprecated
    private static final int TICKET = 1;

    /**
     * Map from consumer tag to {@link Consumer} instance.
     *
     * Note that, in general, this map should ONLY ever be accessed
     * from the connection's reader thread. We go to some pains to
     * ensure this is the case - see the use of
     * BlockingRpcContinuation to inject code into the reader thread
     * in basicConsume and basicCancel.
     */
    public final Map<String, Consumer> _consumers =
        Collections.synchronizedMap(new HashMap<String, Consumer>());

    /** Reference to the currently-active ReturnListener, or null if there is none.
     */
    public volatile ReturnListener returnListener = null;

    /**
     * Construct a new channel on the given connection with the given
     * channel number. Usually not called directly - call
     * Connection.createChannel instead.
     * @see Connection#createChannel
     * @param connection The connection associated with this channel
     * @param channelNumber The channel number to be associated with this channel
     */
    public ChannelN(AMQConnection connection, int channelNumber) {
        super(connection, channelNumber);
    }

    /**
     * Package method: open the channel.
     * This is only called from AMQConnection.
     * @throws java.io.IOException if any problem is encountered
     */
    public void open() throws IOException {
        // wait for the Channel.OpenOk response, then ignore it
        Channel.OpenOk openOk =
            (Channel.OpenOk) exnWrappingRpc(new Channel.Open(UNSPECIFIED_OUT_OF_BAND)).getMethod();
        Utility.use(openOk);
    }

    /** Returns the current ReturnListener. */
    public ReturnListener getReturnListener() {
        return returnListener;
    }

    /**
     * Sets the current ReturnListener.
     * A null argument is interpreted to mean "do not use a return listener".
     */
    public void setReturnListener(ReturnListener listener) {
        returnListener = listener;
    }

    /**
     * Protected API - sends a ShutdownSignal to all active consumers.
     * @param signal an exception signalling channel shutdown
     */
    public void broadcastShutdownSignal(ShutdownSignalException signal) {
        Map<String, Consumer> snapshotConsumers;
        synchronized (_consumers) {
            snapshotConsumers = new HashMap<String, Consumer>(_consumers);
        }
        for (Map.Entry<String,Consumer> entry: snapshotConsumers.entrySet()) {
            Consumer callback = entry.getValue();
            try {
                callback.handleShutdownSignal(entry.getKey(), signal);
            } catch (Throwable ex) {
                _connection.getExceptionHandler().handleConsumerException(this,
                                                                          ex,
                                                                          callback,
                                                                          entry.getKey(),
                                                                          "handleShutdownSignal");
            }
        }
    }

    /**
     * Protected API - overridden to broadcast the signal to all
     * consumers before calling the superclass's method.
     */
    @Override public void processShutdownSignal(ShutdownSignalException signal,
                                                boolean ignoreClosed,
                                                boolean notifyRpc)
    {
        super.processShutdownSignal(signal, ignoreClosed, notifyRpc);
        broadcastShutdownSignal(signal);
    }

    public void releaseChannelNumber() {
        _connection.disconnectChannel(_channelNumber);
    }

    /**
     * Protected API - Filters the inbound command stream, processing
     * Basic.Deliver, Basic.Return and Channel.Close specially.
     */
    @Override public boolean processAsync(Command command) throws IOException
    {
        // If we are isOpen(), then we process commands normally.
        //
        // If we are not, however, then we are in a quiescing, or
        // shutting-down state as the result of an application
        // decision to close this channel, and we are to discard all
        // incoming commands except for a close-ok.

        Method method = command.getMethod();

        if (method instanceof Channel.Close) {
            // Channel should always respond to Channel.Close
            // from the server
            releaseChannelNumber();
            ShutdownSignalException signal = new ShutdownSignalException(false,
                                                                         false,
                                                                         command,
                                                                         this);
            synchronized (_channelMutex) {
                try {
                    processShutdownSignal(signal, true, false);
                    quiescingTransmit(new Channel.CloseOk());
                } finally {
                    notifyOutstandingRpc(signal);
                }
            }
            notifyListeners();
            return true;
        }
        if (isOpen()) {
            // We're in normal running mode.

            if (method instanceof Basic.Deliver) {
                Basic.Deliver m = (Basic.Deliver) method;

                Consumer callback = _consumers.get(m.consumerTag);
                if (callback == null) {
                    // FIXME: what to do when we get such an unsolicited delivery?
                    throw new UnsupportedOperationException("FIXME unsolicited delivery");
                }

                Envelope envelope = new Envelope(m.deliveryTag,
                                                 m.redelivered,
                                                 m.exchange,
                                                 m.routingKey);
                try {
                    callback.handleDelivery(m.consumerTag,
                                            envelope,
                                            (BasicProperties) command.getContentHeader(),
                                            command.getContentBody());
                } catch (Throwable ex) {
                    _connection.getExceptionHandler().handleConsumerException(this,
                                                                              ex,
                                                                              callback,
                                                                              m.consumerTag,
                                                                              "handleDelivery");
                }
                return true;
            } else if (method instanceof Basic.Return) {
                ReturnListener l = getReturnListener();
                if (l != null) {
                    Basic.Return basicReturn = (Basic.Return) method;
                    try {
                        l.handleBasicReturn(basicReturn.replyCode,
                                            basicReturn.replyText,
                                            basicReturn.exchange,
                                            basicReturn.routingKey,
                                            (BasicProperties)
                                            command.getContentHeader(),
                                            command.getContentBody());
                    } catch (Throwable ex) {
                        _connection.getExceptionHandler().handleReturnListenerException(this,
                                                                                        ex);
                    }
                }
                return true;
            } else if (method instanceof Channel.Flow) {
                Channel.Flow channelFlow = (Channel.Flow) method;
                synchronized (_channelMutex) {
                    _blockContent = !channelFlow.active;
                    transmit(new Channel.FlowOk(channelFlow.active));
                    _channelMutex.notifyAll();
                }
                return true;
            } else {
                return false;
            }
        } else {
            // We're in quiescing mode.

            if (method instanceof Channel.CloseOk) {
                // We're quiescing, and we see a channel.close-ok:
                // this is our signal to leave quiescing mode and
                // finally shut down for good. Let it be handled as an
                // RPC reply one final time by returning false.
                return false;
            } else {
                // We're quiescing, and this inbound command should be
                // discarded as per spec. "Consume" it by returning
                // true.
                return true;
            }
        }
    }

    public void close()
        throws IOException
    {
        close(AMQP.REPLY_SUCCESS, "OK");
    }
    
    public void close(int closeCode, String closeMessage)
        throws IOException
    {
        close(closeCode, closeMessage, true, null, false);
    }
    
    public void abort()
        throws IOException
    {
        abort(AMQP.REPLY_SUCCESS, "OK");
    }
    
    public void abort(int closeCode, String closeMessage)
        throws IOException
    {
        close(closeCode, closeMessage, true, null, true);
    }

    /**
     * Protected API - Close channel with code and message, indicating
     * the source of the closure and a causing exception (null if
     * none).
     */
    public void close(int closeCode,
                      String closeMessage,
                      boolean initiatedByApplication,
                      Throwable cause,
                      boolean abort)
        throws IOException
    {
        // First, notify all our dependents that we are shutting down.
        // This clears _isOpen, so no further work from the
        // application side will be accepted, and any inbound commands
        // will be discarded (unless they're channel.close-oks).
        Channel.Close reason = new Channel.Close(closeCode, closeMessage, 0, 0);
        ShutdownSignalException signal = new ShutdownSignalException(false,
                                                                     initiatedByApplication,
                                                                     reason,
                                                                     this);
        if (cause != null) {
            signal.initCause(cause);
        }
        
        BlockingRpcContinuation<AMQCommand> k = new SimpleBlockingRpcContinuation();
        boolean notify = false;
        try {
            // Synchronize the block below to avoid race conditions in case
            // connnection wants to send Connection-CloseOK
            synchronized (_channelMutex) {
                processShutdownSignal(signal, !initiatedByApplication, true);
                quiescingRpc(reason, k);
            }
            
            // Now that we're in quiescing state, channel.close was sent and
            // we wait for the reply. We ignore the result. (It's always
            // close-ok.)
            notify = true;
            k.getReply(-1);
        } catch (TimeoutException ise) {
            // Will never happen since we wait infinitely
        } catch (ShutdownSignalException sse) {
            if (!abort)
                throw sse;
        } catch (IOException ioe) {
            if (!abort)
                throw ioe;
        } finally {
            if (abort || notify) {
                // Now we know everything's been cleaned up and there should
                // be no more surprises arriving on the wire. Release the
                // channel number, and dissociate this ChannelN instance from
                // our connection so that any further frames inbound on this
                // channel can be caught as the errors they are.
                releaseChannelNumber();
                notifyListeners();
            }
        }
    }    

    public void basicQos(int prefetchSize, int prefetchCount, boolean global)
	throws IOException
    {
	exnWrappingRpc(new Basic.Qos(prefetchSize, prefetchCount, global));
    }

    public void basicQos(int prefetchCount)
	throws IOException
    {
	basicQos(0, prefetchCount, false);
    }

    /**
     * Public API - Publish a message with both "mandatory" and
     * "immediate" flags set to false
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     */
    public void basicPublish(String exchange, String routingKey,
                             BasicProperties props, byte[] body)
        throws IOException
    {
        basicPublish(exchange, routingKey, false, false, props, body);
    }

    /**
     * Public API - Publish a message
     * @see com.rabbitmq.client.AMQP.Basic.Publish
     */
    public void basicPublish(String exchange, String routingKey,
                             boolean mandatory, boolean immediate,
                             BasicProperties props, byte[] body)
        throws IOException
    {
        BasicProperties useProps = props;
        if (props == null) {
            useProps = MessageProperties.MINIMAL_BASIC;
        }
        transmit(new AMQCommand(new Basic.Publish(TICKET, exchange, routingKey,
                                                  mandatory, immediate),
                                useProps, body));
    }

    /**
     * Public API - Declare an exchange
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type,
                                              boolean passive, boolean durable,
                                              boolean autoDelete, Map<String, Object> arguments)
        throws IOException
    {
        return (Exchange.DeclareOk)
            exnWrappingRpc(new Exchange.Declare(TICKET, exchange, type,
                                                passive, durable, autoDelete,
                                                false, false, arguments)).getMethod();
    }

    /**
     * Public API - Actively declare a non-autodelete exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type,
                                              boolean durable)
        throws IOException
    {
        return exchangeDeclare(exchange, type, false, durable, false, null);
    }

    /**
     * Public API - Actively declare a non-autodelete, non-durable exchange with no extra arguments
     * @see com.rabbitmq.client.AMQP.Exchange.Declare
     * @see com.rabbitmq.client.AMQP.Exchange.DeclareOk
     */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type)
        throws IOException
    {
        return exchangeDeclare(exchange, type, false, false, false, null);
    }

    /**
     * Public API - Delete an exchange
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     */
    public Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused)
        throws IOException
    {
        return (Exchange.DeleteOk)
            exnWrappingRpc(new Exchange.Delete(TICKET, exchange, ifUnused, false)).getMethod();
    }

    /**
     * Public API - Delete an exchange, without regard for whether it is in use or not
     * @see com.rabbitmq.client.AMQP.Exchange.Delete
     * @see com.rabbitmq.client.AMQP.Exchange.DeleteOk
     */
    public Exchange.DeleteOk exchangeDelete(String exchange)
        throws IOException
    {
        return exchangeDelete(exchange, false);
    }

    /**
     * Public API - Declare a queue
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     */
    public Queue.DeclareOk queueDeclare(String queue, boolean passive,
                                        boolean durable, boolean exclusive,
                                        boolean autoDelete, Map<String, Object> arguments)
        throws IOException
    {
        return (Queue.DeclareOk)
            exnWrappingRpc(new Queue.Declare(TICKET, queue, passive, durable,
                                             exclusive, autoDelete, false, arguments)).getMethod();
    }

    /**
     * Public API - Actively declare a non-exclusive, non-autodelete queue
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     */
    public Queue.DeclareOk queueDeclare(String queue, boolean durable)
        throws IOException
    {
        return queueDeclare(queue, false, durable, false, false, null);
    }

    /**
     * Public API - Actively declare a non-exclusive, non-autodelete, non-durable queue
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     */
    public Queue.DeclareOk queueDeclare(String queue)
        throws IOException
    {
        return queueDeclare(queue, false, false, false, false, null);
    }

    /**
     * Public API - Actively declare a server-named exclusive,
     * autodelete, non-durable queue. The name of the new queue is
     * held in the "queue" field of the {@link com.rabbitmq.client.AMQP.Queue.DeclareOk}
     * result.
     * @see com.rabbitmq.client.AMQP.Queue.Declare
     * @see com.rabbitmq.client.AMQP.Queue.DeclareOk
     */
    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare()
        throws IOException
    {
        return queueDeclare("", false, false, true, true, null);
    }

    /**
     * Public API - Delete a queue
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     */
    public Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty)
        throws IOException
    {
        return (Queue.DeleteOk)
            exnWrappingRpc(new Queue.Delete(TICKET, queue, ifUnused, ifEmpty, false)).getMethod();
    }

    /**
     * Public API - Delete a queue, without regard for whether it is in use or has messages on it
     * @see com.rabbitmq.client.AMQP.Queue.Delete
     * @see com.rabbitmq.client.AMQP.Queue.DeleteOk
     */
    public Queue.DeleteOk queueDelete(String queue)
    throws IOException
    {
        return queueDelete(queue, false, false);
    }

    /**
     * Public API - Bind a queue to an exchange.
     * @see com.rabbitmq.client.AMQP.Queue.Bind
     * @see com.rabbitmq.client.AMQP.Queue.BindOk
     */
    public Queue.BindOk queueBind(String queue, String exchange,
                                  String routingKey, Map<String, Object> arguments)
        throws IOException
    {
        return (Queue.BindOk)
            exnWrappingRpc(new Queue.Bind(TICKET, queue, exchange, routingKey,
                                          false, arguments)).getMethod();
    }

    /**
     * Public API - Bind a queue to an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Queue.Bind
     * @see com.rabbitmq.client.AMQP.Queue.BindOk
     */
    public Queue.BindOk queueBind(String queue, String exchange, String routingKey)
        throws IOException
    {

        return queueBind(queue, exchange, routingKey, null);
    }

    /**
     * Public API - Unbind a queue from an exchange.
     * @see com.rabbitmq.client.AMQP.Queue.Unbind
     * @see com.rabbitmq.client.AMQP.Queue.UnbindOk
     */
    public Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey,
                                      Map<String, Object> arguments)
        throws IOException
    {
        return (Queue.UnbindOk)
            exnWrappingRpc(new Queue.Unbind(TICKET, queue, exchange, routingKey,
                                            arguments)).getMethod();
    }

    /**
     * Public API - Unbind a queue from an exchange, with no extra arguments.
     * @see com.rabbitmq.client.AMQP.Queue.Unbind
     * @see com.rabbitmq.client.AMQP.Queue.UnbindOk
     */
    public Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey)
        throws IOException
    {
        return queueUnbind(queue, exchange, routingKey, null);
    }

    /**
     * Public API - Retrieve a message from a queue using {@link com.rabbitmq.client.AMQP.Basic.Get}
     * @see com.rabbitmq.client.AMQP.Basic.Get
     * @see com.rabbitmq.client.AMQP.Basic.GetOk
     * @see com.rabbitmq.client.AMQP.Basic.GetEmpty
     */
    public GetResponse basicGet(String queue, boolean noAck)
        throws IOException
    {
        AMQCommand replyCommand = exnWrappingRpc(new Basic.Get(TICKET, queue, noAck));
        Method method = replyCommand.getMethod();

        if (method instanceof Basic.GetOk) {
            Basic.GetOk getOk = (Basic.GetOk)method;
            Envelope envelope = new Envelope(getOk.deliveryTag,
                                             getOk.redelivered,
                                             getOk.exchange,
                                             getOk.routingKey);
            BasicProperties props = (BasicProperties)replyCommand.getContentHeader();
            byte[] body = replyCommand.getContentBody();
            int messageCount = getOk.messageCount;
            return new GetResponse(envelope, props, body, messageCount);
        } else if (method instanceof Basic.GetEmpty) {
            return null;
        } else {
            throw new UnexpectedMethodError(method);
        }
    }

    /**
     * Public API - Acknowledge one or several received
     * messages. Supply the deliveryTag from the {@link com.rabbitmq.client.AMQP.Basic.GetOk}
     * or {@link com.rabbitmq.client.AMQP.Basic.Deliver} method
     * containing the received message being acknowledged.
     * @see com.rabbitmq.client.AMQP.Basic.Ack
     */
    public void basicAck(long deliveryTag, boolean multiple)
        throws IOException
    {
        transmit(new Basic.Ack(deliveryTag, multiple));
    }

    /**
     * Public API - Start a non-nolocal, non-exclusive consumer, with
     * explicit acknowledgements required and a server-generated consumerTag.
     * @return the consumerTag generated by the server
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see #basicAck
     * @see com.rabbitmq.client.Channel#basicConsume(String,boolean, String,boolean,boolean,com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, Consumer callback)
        throws IOException
    {
        return basicConsume(queue, false, callback);
    }

    /**
     * Public API - Start a non-nolocal, non-exclusive consumer, with a server-generated consumerTag.
     * @return the consumerTag generated by the server
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see com.rabbitmq.client.Channel#basicConsume(String,boolean, String,boolean,boolean,com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, boolean noAck, Consumer callback)
        throws IOException
    {
        return basicConsume(queue, noAck, "", callback);
    }

    /**
     * Public API - Start a non-nolocal, non-exclusive consumer.
     * @return the consumerTag associated with the new consumer
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @see com.rabbitmq.client.Channel#basicConsume(String,boolean, String,boolean,boolean,com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, boolean noAck, String consumerTag,
                               Consumer callback)
        throws IOException
    {
        return basicConsume(queue, noAck, consumerTag, false, false, callback);
    }

    /**
     * Public API - Start a consumer. Calls the consumer's {@link Consumer#handleConsumeOk}
     * method before returning.
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     * @return the consumerTag associated with the new consumer
     */
    public String basicConsume(String queue, boolean noAck, String consumerTag,
                               boolean noLocal, boolean exclusive,
                               final Consumer callback)
        throws IOException
    {
        BlockingRpcContinuation<String> k = new BlockingRpcContinuation<String>() {
            public String transformReply(AMQCommand replyCommand) {
                String actualConsumerTag = ((Basic.ConsumeOk) replyCommand.getMethod()).consumerTag;
                _consumers.put(actualConsumerTag, callback);
                // We need to call back inside the connection thread
                // in order avoid races with 'deliver' commands
                try {
                    callback.handleConsumeOk(actualConsumerTag);
                } catch (Throwable ex) {
                    _connection.getExceptionHandler().handleConsumerException(ChannelN.this,
                                                                              ex,
                                                                              callback,
                                                                              actualConsumerTag,
                                                                              "handleConsumeOk");
                }
                return actualConsumerTag;
            }
        };

        rpc(new Basic.Consume(TICKET, queue, consumerTag,
                              noLocal, noAck, exclusive,
                              false),
            k);

        try {
            return k.getReply();
        } catch(ShutdownSignalException ex) {
            throw wrap(ex);
        }
    }

    /**
     * Public API - Cancel a consumer. Calls the consumer's {@link Consumer#handleCancelOk}
     * method before returning.
     * @see com.rabbitmq.client.AMQP.Basic.Cancel
     * @see com.rabbitmq.client.AMQP.Basic.CancelOk
     */
    public void basicCancel(final String consumerTag)
        throws IOException
    {
        BlockingRpcContinuation<Consumer> k = new BlockingRpcContinuation<Consumer>() {
            public Consumer transformReply(AMQCommand replyCommand) {
                Basic.CancelOk dummy = (Basic.CancelOk) replyCommand.getMethod();
                Utility.use(dummy);
                Consumer callback = _consumers.remove(consumerTag);
                // We need to call back inside the connection thread
                // in order avoid races with 'deliver' commands
                try {
                    callback.handleCancelOk(consumerTag);
                } catch (Throwable ex) {
                    _connection.getExceptionHandler().handleConsumerException(ChannelN.this,
                                                                              ex,
                                                                              callback,
                                                                              consumerTag,
                                                                              "handleCancelOk");
                }
                return callback;
            }
        };

        rpc(new Basic.Cancel(consumerTag, false), k);

        try {
            Consumer callback = k.getReply();
            Utility.use(callback);
        } catch(ShutdownSignalException ex) {
            throw wrap(ex);
        }
    }

    /**
     * Public API - Enables TX mode on this channel.
     * @see com.rabbitmq.client.AMQP.Tx.Select
     * @see com.rabbitmq.client.AMQP.Tx.SelectOk
     */
    public Tx.SelectOk txSelect()
        throws IOException
    {
        return (Tx.SelectOk) exnWrappingRpc(new Tx.Select()).getMethod();
    }

    /**
     * Public API - Commits a TX transaction on this channel.
     * @see com.rabbitmq.client.AMQP.Tx.Commit
     * @see com.rabbitmq.client.AMQP.Tx.CommitOk
     */
    public Tx.CommitOk txCommit()
        throws IOException
    {
        return (Tx.CommitOk) exnWrappingRpc(new Tx.Commit()).getMethod();
    }

    /**
     * Public API - Rolls back a TX transaction on this channel.
     * @see com.rabbitmq.client.AMQP.Tx.Rollback
     * @see com.rabbitmq.client.AMQP.Tx.RollbackOk
     */
    public Tx.RollbackOk txRollback()
        throws IOException
    {
        return (Tx.RollbackOk) exnWrappingRpc(new Tx.Rollback()).getMethod();
    }
}
