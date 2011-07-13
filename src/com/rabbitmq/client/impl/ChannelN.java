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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.FlowListener;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.UnexpectedMethodError;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.AMQImpl.Basic;
import com.rabbitmq.client.impl.AMQImpl.Channel;
import com.rabbitmq.client.impl.AMQImpl.Confirm;
import com.rabbitmq.client.impl.AMQImpl.Exchange;
import com.rabbitmq.client.impl.AMQImpl.Queue;
import com.rabbitmq.client.impl.AMQImpl.Tx;
import com.rabbitmq.utility.Utility;


/**
 * Main interface to AMQP protocol functionality. Public API -
 * Implementation of all AMQChannels except channel zero.
 * <p>
 * To open a channel,
 * <pre>
 * {@link Connection} conn = ...;
 * {@link ChannelN} ch1 = conn.{@link Connection#createChannel createChannel}();
 * </pre>
 */
public class ChannelN extends AMQChannel implements com.rabbitmq.client.Channel {
    private static final String UNSPECIFIED_OUT_OF_BAND = "";

    /**
     * When 0.9.1 is signed off, tickets can be removed from the codec
     * and this field can be deleted.
     */
    @Deprecated
    private static final int TICKET = 0;

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

    /** Reference to the currently-active FlowListener, or null if there is none.
     */
    public volatile FlowListener flowListener = null;

    /** Reference to the currently-active ConfirmListener, or null if there is none.
     */
    public volatile ConfirmListener confirmListener = null;

    /** Sequence number of next published message requiring confirmation.
     */
    private long nextPublishSeqNo = 0L;

    /** Reference to the currently-active default consumer, or null if there is
     *  none.
     */
    public volatile Consumer defaultConsumer = null;

    /** Set of currently unconfirmed messages (i.e. messages that have
     * not been ack'd or nack'd by the server yet. */
    protected volatile SortedSet<Long> unconfirmedSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());

    /** Whether any nacks have been received since the last
     * waitForConfirms(). */
    protected volatile boolean onlyAcksReceived = true;

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

    /** Returns the current {@link FlowListener}. */
    public FlowListener getFlowListener() {
        return flowListener;
    }

    /**
     * Sets the current {@link FlowListener}.
     * A null argument is interpreted to mean "do not use a flow listener".
     */
    public void setFlowListener(FlowListener listener) {
        flowListener = listener;
    }

    /** Returns the current {@link ConfirmListener}. */
    public ConfirmListener getConfirmListener() {
        return confirmListener;
    }

    /** {@inheritDoc} */
    public boolean waitForConfirms()
        throws IOException, InterruptedException
    {
        synchronized (unconfirmedSet) {
            while (true) {
                if (getCloseReason() != null) {
                    throw new IOException(Utility.fixStackTrace(getCloseReason()));
                }
                if (unconfirmedSet.isEmpty()) {
                    boolean aux = onlyAcksReceived;
                    onlyAcksReceived = true;
                    return aux;
                }
                unconfirmedSet.wait();
            }
        }
    }

    /** {@inheritDoc} */
    public void waitForConfirmsOrDie()
        throws IOException, InterruptedException
    {
        if (!waitForConfirms()) {
            close(AMQP.REPLY_SUCCESS, "NACKS RECEIVED", true,
                  new RuntimeException("received nack"), false);
            throw new IOException(Utility.fixStackTrace(getCloseReason()));
        }
    }

    /**
     * Sets the current {@link ConfirmListener}.
     * A null argument is interpreted to mean "do not use a confirm listener".
     */
    public void setConfirmListener(ConfirmListener listener) {
        confirmListener = listener;
    }

    /** Returns the current default consumer. */
    public Consumer getDefaultConsumer() {
        return defaultConsumer;
    }

    /**
     * Sets the current default consumer.
     * A null argument is interpreted to mean "do not use a default consumer".
     */
    public void setDefaultConsumer(Consumer consumer) {
        defaultConsumer = consumer;
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
        synchronized (unconfirmedSet) {
            unconfirmedSet.notify();
        }
    }

    public void releaseChannelNumber() {
        _connection.disconnectChannel(this);
    }

    /**
     * Protected API - Filters the inbound command stream, processing
     * Basic.Deliver, Basic.Return and Channel.Close specially.  If
     * we're in quiescing mode, all inbound commands are ignored,
     * except for Channel.Close and Channel.CloseOk.
     */
    @Override public boolean processAsync(Command command) throws IOException
    {
        // If we are isOpen(), then we process commands normally.
        //
        // If we are not, however, then we are in a quiescing, or
        // shutting-down state as the result of an application
        // decision to close this channel, and we are to discard all
        // incoming commands except for a close and close-ok.

        Method method = command.getMethod();
        // we deal with channel.close in the same way, regardless
        if (method instanceof Channel.Close) {
            asyncShutdown(command);
            return true;
        }

        if (isOpen()) {
            // We're in normal running mode.

            if (method instanceof Basic.Deliver) {
                Basic.Deliver m = (Basic.Deliver) method;

                Consumer callback = _consumers.get(m.getConsumerTag());
                if (callback == null) {
                    if (defaultConsumer == null) {
                        // No handler set. We should blow up as this message
                        // needs acking, just dropping it is not enough. See bug
                        // 22587 for discussion.
                        throw new IllegalStateException("Unsolicited delivery -" +
                                " see Channel.setDefaultConsumer to handle this" +
                                " case.");
                    }
                    else {
                        callback = defaultConsumer;
                    }
                }

                Envelope envelope = new Envelope(m.getDeliveryTag(),
                                                 m.getRedelivered(),
                                                 m.getExchange(),
                                                 m.getRoutingKey());
                try {
                    callback.handleDelivery(m.getConsumerTag(),
                                            envelope,
                                            (BasicProperties) command.getContentHeader(),
                                            command.getContentBody());
                } catch (Throwable ex) {
                    _connection.getExceptionHandler().handleConsumerException(this,
                                                                              ex,
                                                                              callback,
                                                                              m.getConsumerTag(),
                                                                              "handleDelivery");
                }
                return true;
            } else if (method instanceof Basic.Return) {
                ReturnListener l = getReturnListener();
                if (l != null) {
                    Basic.Return basicReturn = (Basic.Return) method;
                    try {
                        l.handleReturn(basicReturn.getReplyCode(),
                                            basicReturn.getReplyText(),
                                            basicReturn.getExchange(),
                                            basicReturn.getRoutingKey(),
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
                    _blockContent = !channelFlow.getActive();
                    transmit(new Channel.FlowOk(!_blockContent));
                    _channelMutex.notifyAll();
                }
                FlowListener l = getFlowListener();
                if (l != null) {
                    try {
                        l.handleFlow(channelFlow.getActive());
                    } catch (Throwable ex) {
                        _connection.getExceptionHandler().handleFlowListenerException(this, ex);
                    }
                }
                return true;
            } else if (method instanceof Basic.Ack) {
                Basic.Ack ack = (Basic.Ack) method;
                try {
                    if (confirmListener != null)
                        confirmListener.handleAck(ack.getDeliveryTag(), ack.getMultiple());
                } catch (Throwable ex) {
                    _connection.getExceptionHandler().handleConfirmListenerException(this, ex);
                }
                handleAckNack(ack.getDeliveryTag(), ack.getMultiple(), false);
                return true;
            } else if (method instanceof Basic.Nack) {
                Basic.Nack nack = (Basic.Nack) method;
                try {
                    if (confirmListener != null)
                        confirmListener.handleNack(nack.getDeliveryTag(), nack.getMultiple());
                } catch (Throwable ex) {
                    _connection.getExceptionHandler().handleConfirmListenerException(this, ex);
                }
                handleAckNack(nack.getDeliveryTag(), nack.getMultiple(), true);
                return true;
            } else if (method instanceof Basic.RecoverOk) {
                for (Consumer callback: _consumers.values()) {
                    callback.handleRecoverOk();
                }

                // Unlike all the other cases we still want this RecoverOk to
                // be handled by whichever RPC continuation invoked Recover,
                // so return false
                return false;
            } else if (method instanceof Basic.Cancel) {
                Basic.Cancel m = (Basic.Cancel)method;
                String consumerTag = m.getConsumerTag();
                Consumer callback = _consumers.remove(consumerTag);
                if (callback == null) {
                    callback = defaultConsumer;
                }
                if (callback != null) {
                    try {
                        callback.handleCancel(consumerTag);
                    } catch (Throwable ex) {
                        _connection.getExceptionHandler().handleConsumerException(this,
                                                                                  ex,
                                                                                  callback,
                                                                                  consumerTag,
                                                                                  "handleCancel");
                    }
                }
                return true;
            } else {
                return false;
            }
        } else {
            // We're in quiescing mode == !isOpen()

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

    private void asyncShutdown(Command command) throws IOException {
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
    }

    /** Public API - {@inheritDoc} */
    public void close()
        throws IOException
    {
        close(AMQP.REPLY_SUCCESS, "OK");
    }

    /** Public API - {@inheritDoc} */
    public void close(int closeCode, String closeMessage)
        throws IOException
    {
        close(closeCode, closeMessage, true, null, false);
    }

    /** Public API - {@inheritDoc} */
    public void abort()
        throws IOException
    {
        abort(AMQP.REPLY_SUCCESS, "OK");
    }

    /** Public API - {@inheritDoc} */
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
        // This clears isOpen(), so no further work from the
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
            // we wait for the reply. We ignore the result.
            // (It's NOT always close-ok.)
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

    /** Public API - {@inheritDoc} */
    public void basicQos(int prefetchSize, int prefetchCount, boolean global)
	throws IOException
    {
	exnWrappingRpc(new Basic.Qos(prefetchSize, prefetchCount, global));
    }

    /** Public API - {@inheritDoc} */
    public void basicQos(int prefetchCount)
	throws IOException
    {
	basicQos(0, prefetchCount, false);
    }

    /** Public API - {@inheritDoc} */
    public void basicPublish(String exchange, String routingKey,
                             BasicProperties props, byte[] body)
        throws IOException
    {
        basicPublish(exchange, routingKey, false, false, props, body);
    }

    /** Public API - {@inheritDoc} */
    public void basicPublish(String exchange, String routingKey,
                             boolean mandatory, boolean immediate,
                             BasicProperties props, byte[] body)
        throws IOException
    {
        if (nextPublishSeqNo > 0) {
            unconfirmedSet.add(getNextPublishSeqNo());
            nextPublishSeqNo++;
        }
        BasicProperties useProps = props;
        if (props == null) {
            useProps = MessageProperties.MINIMAL_BASIC;
        }
        transmit(new AMQCommand(new Basic.Publish(TICKET, exchange, routingKey,
                                                  mandatory, immediate),
                                useProps, body));
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type,
                                              boolean durable, boolean autoDelete,
                                              Map<String, Object> arguments)
        throws IOException
    {
        return exchangeDeclare(exchange, type,
                               durable, autoDelete, false,
                               arguments);
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type,
                                              boolean durable,
                                              boolean autoDelete,
                                              boolean internal,
                                              Map<String, Object> arguments)
            throws IOException
    {
        return (Exchange.DeclareOk)
                exnWrappingRpc(new Exchange.Declare(TICKET, exchange, type,
                                                    false, durable, autoDelete,
                                                    internal, false,
                                                    arguments)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type,
                                              boolean durable)
        throws IOException
    {
        return exchangeDeclare(exchange, type, durable, false, null);
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeclareOk exchangeDeclare(String exchange, String type)
        throws IOException
    {
        return exchangeDeclare(exchange, type, false, false, null);
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeclareOk exchangeDeclarePassive(String exchange)
        throws IOException
    {
        return (Exchange.DeclareOk)
            exnWrappingRpc(new Exchange.Declare(TICKET, exchange, "",
                                                true, false, false,
                                                false, false, null)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused)
        throws IOException
    {
        return (Exchange.DeleteOk)
            exnWrappingRpc(new Exchange.Delete(TICKET, exchange, ifUnused, false)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeleteOk exchangeDelete(String exchange)
        throws IOException
    {
        return exchangeDelete(exchange, false);
    }

    /** Public API - {@inheritDoc} */
    public Exchange.BindOk exchangeBind(String destination, String source,
            String routingKey, Map<String, Object> arguments)
            throws IOException {
        return (Exchange.BindOk) exnWrappingRpc(
                new Exchange.Bind(TICKET, destination, source, routingKey,
                        false, arguments)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.BindOk exchangeBind(String destination, String source,
            String routingKey) throws IOException {
        return exchangeBind(destination, source, routingKey, null);
    }

    /** Public API - {@inheritDoc} */
    public Exchange.UnbindOk exchangeUnbind(String destination, String source,
            String routingKey, Map<String, Object> arguments)
            throws IOException {
        return (Exchange.UnbindOk) exnWrappingRpc(
                new Exchange.Unbind(TICKET, destination, source, routingKey,
                        false, arguments)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.UnbindOk exchangeUnbind(String destination, String source,
            String routingKey) throws IOException {
        return exchangeUnbind(destination, source, routingKey, null);
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive,
                                        boolean autoDelete, Map<String, Object> arguments)
        throws IOException
    {
        return (Queue.DeclareOk)
            exnWrappingRpc(new Queue.Declare(TICKET, queue, false, durable,
                                             exclusive, autoDelete, false, arguments)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare()
        throws IOException
    {
        return queueDeclare("", false, true, true, null);
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeclareOk queueDeclarePassive(String queue)
        throws IOException
    {
        return (Queue.DeclareOk)
            exnWrappingRpc(new Queue.Declare(TICKET, queue, true, false,
                                             true, true, false, null)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty)
        throws IOException
    {
        return (Queue.DeleteOk)
            exnWrappingRpc(new Queue.Delete(TICKET, queue, ifUnused, ifEmpty, false)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeleteOk queueDelete(String queue)
        throws IOException
    {
        return queueDelete(queue, false, false);
    }

    /** Public API - {@inheritDoc} */
    public Queue.BindOk queueBind(String queue, String exchange,
                                  String routingKey, Map<String, Object> arguments)
        throws IOException
    {
        return (Queue.BindOk)
            exnWrappingRpc(new Queue.Bind(TICKET, queue, exchange, routingKey,
                                          false, arguments)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.BindOk queueBind(String queue, String exchange, String routingKey)
        throws IOException
    {

        return queueBind(queue, exchange, routingKey, null);
    }

    /** Public API - {@inheritDoc} */
    public Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey,
                                      Map<String, Object> arguments)
        throws IOException
    {
        return (Queue.UnbindOk)
            exnWrappingRpc(new Queue.Unbind(TICKET, queue, exchange, routingKey,
                                            arguments)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.PurgeOk queuePurge(String queue)
        throws IOException
    {
        return (Queue.PurgeOk)
            exnWrappingRpc(new Queue.Purge(TICKET, queue, false)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey)
        throws IOException
    {
        return queueUnbind(queue, exchange, routingKey, null);
    }

    /** Public API - {@inheritDoc} */
    public GetResponse basicGet(String queue, boolean autoAck)
        throws IOException
    {
        AMQCommand replyCommand = exnWrappingRpc(new Basic.Get(TICKET, queue, autoAck));
        Method method = replyCommand.getMethod();

        if (method instanceof Basic.GetOk) {
            Basic.GetOk getOk = (Basic.GetOk)method;
            Envelope envelope = new Envelope(getOk.getDeliveryTag(),
                                             getOk.getRedelivered(),
                                             getOk.getExchange(),
                                             getOk.getRoutingKey());
            BasicProperties props = (BasicProperties)replyCommand.getContentHeader();
            byte[] body = replyCommand.getContentBody();
            int messageCount = getOk.getMessageCount();
            return new GetResponse(envelope, props, body, messageCount);
        } else if (method instanceof Basic.GetEmpty) {
            return null;
        } else {
            throw new UnexpectedMethodError(method);
        }
    }

    /** Public API - {@inheritDoc} */
    public void basicAck(long deliveryTag, boolean multiple)
        throws IOException
    {
        transmit(new Basic.Ack(deliveryTag, multiple));
    }

    /** Public API - {@inheritDoc} */
    public void basicNack(long deliveryTag, boolean multiple, boolean requeue)
        throws IOException
    {
        transmit(new Basic.Nack(deliveryTag, multiple, requeue));
    }

    /** Public API - {@inheritDoc} */
    public void basicReject(long deliveryTag, boolean requeue)
        throws IOException
    {
        transmit(new Basic.Reject(deliveryTag, requeue));
    }

    /** Public API - {@inheritDoc} */
    public String basicConsume(String queue, Consumer callback)
        throws IOException
    {
        return basicConsume(queue, false, callback);
    }

    /** Public API - {@inheritDoc} */
    public String basicConsume(String queue, boolean autoAck, Consumer callback)
        throws IOException
    {
        return basicConsume(queue, autoAck, "", callback);
    }

    /** Public API - {@inheritDoc} */
    public String basicConsume(String queue, boolean autoAck, String consumerTag,
                               Consumer callback)
        throws IOException
    {
        return basicConsume(queue, autoAck, consumerTag, false, false, null, callback);
    }

    /** Public API - {@inheritDoc} */
    public String basicConsume(String queue, boolean autoAck, String consumerTag,
                               boolean noLocal, boolean exclusive, Map<String, Object> arguments,
                               final Consumer callback)
        throws IOException
    {
        BlockingRpcContinuation<String> k = new BlockingRpcContinuation<String>() {
            public String transformReply(AMQCommand replyCommand) {
                String actualConsumerTag = ((Basic.ConsumeOk) replyCommand.getMethod()).getConsumerTag();
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
                              noLocal, autoAck, exclusive,
                              false, arguments),
            k);

        try {
            return k.getReply();
        } catch(ShutdownSignalException ex) {
            throw wrap(ex);
        }
    }

    /** Public API - {@inheritDoc} */
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


     /** Public API - {@inheritDoc} */
    public Basic.RecoverOk basicRecover()
        throws IOException
    {
        return basicRecover(true);
    }

     /** Public API - {@inheritDoc} */
    public Basic.RecoverOk basicRecover(boolean requeue)
        throws IOException
    {
        return (Basic.RecoverOk) exnWrappingRpc(new Basic.Recover(requeue)).getMethod();
    }


    /** Public API - {@inheritDoc} */
    public void basicRecoverAsync(boolean requeue)
        throws IOException
    {
        transmit(new Basic.RecoverAsync(requeue));
    }

    /** Public API - {@inheritDoc} */
    public Tx.SelectOk txSelect()
        throws IOException
    {
        return (Tx.SelectOk) exnWrappingRpc(new Tx.Select()).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Tx.CommitOk txCommit()
        throws IOException
    {
        return (Tx.CommitOk) exnWrappingRpc(new Tx.Commit()).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Tx.RollbackOk txRollback()
        throws IOException
    {
        return (Tx.RollbackOk) exnWrappingRpc(new Tx.Rollback()).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Confirm.SelectOk confirmSelect()
        throws IOException
    {
        if (nextPublishSeqNo == 0) nextPublishSeqNo = 1;
        return (Confirm.SelectOk)
            exnWrappingRpc(new Confirm.Select(false)).getMethod();

    }

    /** Public API - {@inheritDoc} */
    public Channel.FlowOk flow(final boolean a) throws IOException {
        return (Channel.FlowOk) exnWrappingRpc(new Channel.Flow(a)).getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Channel.FlowOk getFlow() {
        return new Channel.FlowOk(!_blockContent);
    }

    /** Public API - {@inheritDoc} */
    public long getNextPublishSeqNo() {
        return nextPublishSeqNo;
    }

    public void asyncRpc(com.rabbitmq.client.Method method) throws IOException {
        // This cast should eventually go
        transmit((com.rabbitmq.client.impl.Method)method);
    }

    public com.rabbitmq.client.Method rpc(com.rabbitmq.client.Method method) throws IOException {
        return exnWrappingRpc((com.rabbitmq.client.impl.Method)method).getMethod();
    }

    protected void handleAckNack(long seqNo, boolean multiple, boolean nack) {
        if (multiple) {
            unconfirmedSet.headSet(seqNo + 1).clear();
        } else {
            unconfirmedSet.remove(seqNo);
        }
        synchronized (unconfirmedSet) {
            onlyAcksReceived = onlyAcksReceived && !nack;
            if (unconfirmedSet.isEmpty())
                unconfirmedSet.notify();
        }
    }
}
