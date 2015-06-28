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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.FlowListener;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.UnexpectedMethodError;
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

    /** Map from consumer tag to {@link Consumer} instance.
     * <p/>
     * Note that, in general, this map should ONLY ever be accessed
     * from the connection's reader thread. We go to some pains to
     * ensure this is the case - see the use of
     * BlockingRpcContinuation to inject code into the reader thread
     * in basicConsume and basicCancel.
     */
    private final Map<String, Consumer> _consumers =
        Collections.synchronizedMap(new HashMap<String, Consumer>());

    /* All listeners collections are in CopyOnWriteArrayList objects */
    /** The ReturnListener collection. */
    private final Collection<ReturnListener> returnListeners = new CopyOnWriteArrayList<ReturnListener>();
    /** The FlowListener collection. */
    private final Collection<FlowListener> flowListeners = new CopyOnWriteArrayList<FlowListener>();
    /** The ConfirmListener collection. */
    private final Collection<ConfirmListener> confirmListeners = new CopyOnWriteArrayList<ConfirmListener>();

    /** Sequence number of next published message requiring confirmation.*/
    private long nextPublishSeqNo = 0L;

    /** The current default consumer, or null if there is none. */
    private volatile Consumer defaultConsumer = null;

    /** Dispatcher of consumer work for this channel */
    private final ConsumerDispatcher dispatcher;

    /** Future boolean for shutting down */
    private volatile CountDownLatch finishedShutdownFlag = null;

    /** Set of currently unconfirmed messages (i.e. messages that have
     *  not been ack'd or nack'd by the server yet. */
    private final SortedSet<Long> unconfirmedSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());

    /** Whether any nacks have been received since the last waitForConfirms(). */
    private volatile boolean onlyAcksReceived = true;

    /**
     * Construct a new channel on the given connection with the given
     * channel number. Usually not called directly - call
     * Connection.createChannel instead.
     * @see Connection#createChannel
     * @param connection The connection associated with this channel
     * @param channelNumber The channel number to be associated with this channel
     * @param workService service for managing this channel's consumer callbacks
     */
    public ChannelN(AMQConnection connection, int channelNumber,
                    ConsumerWorkService workService) {
        super(connection, channelNumber);
        this.dispatcher = new ConsumerDispatcher(connection, this, workService);
    }

    /**
     * Package method: open the channel.
     * This is only called from {@link ChannelManager}.
     * @throws IOException if any problem is encountered
     */
    public void open() throws IOException {
        // wait for the Channel.OpenOk response, and ignore it
        exnWrappingRpc(new Channel.Open(UNSPECIFIED_OUT_OF_BAND));
    }

    public void addReturnListener(ReturnListener listener) {
        returnListeners.add(listener);
    }

    public boolean removeReturnListener(ReturnListener listener) {
        return returnListeners.remove(listener);
    }

    public void clearReturnListeners() {
        returnListeners.clear();
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public void addFlowListener(FlowListener listener) {
        flowListeners.add(listener);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public boolean removeFlowListener(FlowListener listener) {
        return flowListeners.remove(listener);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public void clearFlowListeners() {
        flowListeners.clear();
    }

    public void addConfirmListener(ConfirmListener listener) {
        confirmListeners.add(listener);
    }

    public boolean removeConfirmListener(ConfirmListener listener) {
        return confirmListeners.remove(listener);
    }

    public void clearConfirmListeners() {
        confirmListeners.clear();
    }

    /** {@inheritDoc} */
    public boolean waitForConfirms()
        throws InterruptedException
    {
        boolean confirms = false;
        try {
            confirms = waitForConfirms(0L);
        } catch (TimeoutException e) { }
        return confirms;
    }

    /** {@inheritDoc} */
    public boolean waitForConfirms(long timeout)
            throws InterruptedException, TimeoutException {
        if (nextPublishSeqNo == 0L)
            throw new IllegalStateException("Confirms not selected");
        long startTime = System.currentTimeMillis();
        synchronized (unconfirmedSet) {
            while (true) {
                if (getCloseReason() != null) {
                    throw Utility.fixStackTrace(getCloseReason());
                }
                if (unconfirmedSet.isEmpty()) {
                    boolean aux = onlyAcksReceived;
                    onlyAcksReceived = true;
                    return aux;
                }
                if (timeout == 0L) {
                    unconfirmedSet.wait();
                } else {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (timeout > elapsed) {
                        unconfirmedSet.wait(timeout - elapsed);
                    } else {
                        throw new TimeoutException();
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void waitForConfirmsOrDie()
        throws IOException, InterruptedException
    {
        try {
            waitForConfirmsOrDie(0L);
        } catch (TimeoutException e) { }
    }

    /** {@inheritDoc} */
    public void waitForConfirmsOrDie(long timeout)
        throws IOException, InterruptedException, TimeoutException
    {
        try {
            if (!waitForConfirms(timeout)) {
                close(AMQP.REPLY_SUCCESS, "NACKS RECEIVED", true, null, false);
                throw new IOException("nacks received");
            }
        } catch (TimeoutException e) {
            close(AMQP.PRECONDITION_FAILED, "TIMEOUT WAITING FOR ACK");
            throw(e);
        }
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
     * Sends a ShutdownSignal to all active consumers.
     * Idempotent.
     * @param signal an exception signalling channel shutdown
     */
    private void broadcastShutdownSignal(ShutdownSignalException signal) {
        Map<String, Consumer> snapshotConsumers;
        synchronized (_consumers) {
            snapshotConsumers = new HashMap<String, Consumer>(_consumers);
        }
        this.finishedShutdownFlag = this.dispatcher.handleShutdownSignal(snapshotConsumers, signal);
    }

    /**
     * Start to shutdown -- defer rest of processing until ready
     */
    private void startProcessShutdownSignal(ShutdownSignalException signal,
                                                boolean ignoreClosed,
                                                boolean notifyRpc)
    {   super.processShutdownSignal(signal, ignoreClosed, notifyRpc);
    }

    /**
     * Finish shutdown processing -- idempotent
     */
    private void finishProcessShutdownSignal()
    {
        this.dispatcher.quiesce();
        broadcastShutdownSignal(getCloseReason());

        synchronized (unconfirmedSet) {
            unconfirmedSet.notifyAll();
        }
    }

    /**
     * Protected API - overridden to quiesce consumer work and broadcast the signal
     * to all consumers after calling the superclass's method.
     */
    @Override public void processShutdownSignal(ShutdownSignalException signal,
                                                boolean ignoreClosed,
                                                boolean notifyRpc)
    {
        startProcessShutdownSignal(signal, ignoreClosed, notifyRpc);
        finishProcessShutdownSignal();
    }

    CountDownLatch getShutdownLatch() {
        return this.finishedShutdownFlag;
    }

    private void releaseChannel() {
        getConnection().disconnectChannel(this);
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
                processDelivery(command, (Basic.Deliver) method);
                return true;
            } else if (method instanceof Basic.Return) {
                callReturnListeners(command, (Basic.Return) method);
                return true;
            } else if (method instanceof Channel.Flow) {
                Channel.Flow channelFlow = (Channel.Flow) method;
                synchronized (_channelMutex) {
                    _blockContent = !channelFlow.getActive();
                    transmit(new Channel.FlowOk(!_blockContent));
                    _channelMutex.notifyAll();
                }
                callFlowListeners(command, channelFlow);
                return true;
            } else if (method instanceof Basic.Ack) {
                Basic.Ack ack = (Basic.Ack) method;
                callConfirmListeners(command, ack);
                handleAckNack(ack.getDeliveryTag(), ack.getMultiple(), false);
                return true;
            } else if (method instanceof Basic.Nack) {
                Basic.Nack nack = (Basic.Nack) method;
                callConfirmListeners(command, nack);
                handleAckNack(nack.getDeliveryTag(), nack.getMultiple(), true);
                return true;
            } else if (method instanceof Basic.RecoverOk) {
                for (Map.Entry<String, Consumer> entry : _consumers.entrySet()) {
                    this.dispatcher.handleRecoverOk(entry.getValue(), entry.getKey());
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
                        this.dispatcher.handleCancel(callback, consumerTag);
                    } catch (Throwable ex) {
                        getConnection().getExceptionHandler().handleConsumerException(this,
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

    protected void processDelivery(Command command, Basic.Deliver method) {
        Basic.Deliver m = method;

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
            this.dispatcher.handleDelivery(callback,
                                           m.getConsumerTag(),
                                           envelope,
                                           (BasicProperties) command.getContentHeader(),
                                           command.getContentBody());
        } catch (Throwable ex) {
            getConnection().getExceptionHandler().handleConsumerException(this,
                ex,
                callback,
                m.getConsumerTag(),
                "handleDelivery");
        }
    }

    private void callReturnListeners(Command command, Basic.Return basicReturn) {
        try {
            for (ReturnListener l : this.returnListeners) {
                l.handleReturn(basicReturn.getReplyCode(),
                    basicReturn.getReplyText(),
                    basicReturn.getExchange(),
                    basicReturn.getRoutingKey(),
                    (BasicProperties) command.getContentHeader(),
                    command.getContentBody());
            }
        } catch (Throwable ex) {
            getConnection().getExceptionHandler().handleReturnListenerException(this, ex);
        }
    }

    private void callFlowListeners(@SuppressWarnings("unused") Command command, Channel.Flow channelFlow) {
        try {
            for (FlowListener l : this.flowListeners) {
                l.handleFlow(channelFlow.getActive());
            }
        } catch (Throwable ex) {
            getConnection().getExceptionHandler().handleFlowListenerException(this, ex);
        }
    }

    private void callConfirmListeners(@SuppressWarnings("unused") Command command, Basic.Ack ack) {
        try {
            for (ConfirmListener l : this.confirmListeners) {
                l.handleAck(ack.getDeliveryTag(), ack.getMultiple());
            }
        } catch (Throwable ex) {
            getConnection().getExceptionHandler().handleConfirmListenerException(this, ex);
        }
    }

    private void callConfirmListeners(@SuppressWarnings("unused") Command command, Basic.Nack nack) {
        try {
            for (ConfirmListener l : this.confirmListeners) {
                l.handleNack(nack.getDeliveryTag(), nack.getMultiple());
            }
        } catch (Throwable ex) {
            getConnection().getExceptionHandler().handleConfirmListenerException(this, ex);
        }
    }

    private void asyncShutdown(Command command) throws IOException {
        ShutdownSignalException signal = new ShutdownSignalException(false,
                                                                     false,
                                                                     command.getMethod(),
                                                                     this);
        synchronized (_channelMutex) {
            try {
                processShutdownSignal(signal, true, false);
                quiescingTransmit(new Channel.CloseOk());
            } finally {
                releaseChannel();
                notifyOutstandingRpc(signal);
            }
        }
        notifyListeners();
    }

    /** Public API - {@inheritDoc} */
    public void close()
        throws IOException, TimeoutException {
        close(AMQP.REPLY_SUCCESS, "OK");
    }

    /** Public API - {@inheritDoc} */
    public void close(int closeCode, String closeMessage)
        throws IOException, TimeoutException {
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
        try {
          close(closeCode, closeMessage, true, null, true);
        } catch (IOException _e) {
        /* ignored */
        } catch (TimeoutException _e) {
          /* ignored */
        }
    }

    /**
     * Protected API - Close channel with code and message, indicating
     * the source of the closure and a causing exception (null if
     * none).
     * @param closeCode the close code (See under "Reply Codes" in the AMQP specification)
     * @param closeMessage a message indicating the reason for closing the connection
     * @param initiatedByApplication true if this comes from an API call, false otherwise
     * @param cause exception triggering close
     * @param abort true if we should close and ignore errors
     * @throws IOException if an error is encountered
     */
    protected void close(int closeCode,
                      String closeMessage,
                      boolean initiatedByApplication,
                      Throwable cause,
                      boolean abort)
        throws IOException, TimeoutException {
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

        BlockingRpcContinuation<AMQCommand> k = new BlockingRpcContinuation<AMQCommand>(){
            @Override
            public AMQCommand transformReply(AMQCommand command) {
                ChannelN.this.finishProcessShutdownSignal();
                return command;
            }};
        boolean notify = false;
        try {
            // Synchronize the block below to avoid race conditions in case
            // connnection wants to send Connection-CloseOK
            synchronized (_channelMutex) {
                startProcessShutdownSignal(signal, !initiatedByApplication, true);
                quiescingRpc(reason, k);
            }

            // Now that we're in quiescing state, channel.close was sent and
            // we wait for the reply. We ignore the result.
            // (It's NOT always close-ok.)
            notify = true;
			      // do not wait indefinitely
            k.getReply(10000);
        } catch (TimeoutException ise) {
            if (!abort)
                throw ise;
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
                releaseChannel();
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
    public void basicQos(int prefetchCount, boolean global)
            throws IOException
    {
        basicQos(0, prefetchCount, global);
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
        basicPublish(exchange, routingKey, false, props, body);
    }

    /** Public API - {@inheritDoc} */
    public void basicPublish(String exchange, String routingKey,
                             boolean mandatory,
                             BasicProperties props, byte[] body)
        throws IOException
    {
        basicPublish(exchange, routingKey, mandatory, false, props, body);
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
        transmit(new AMQCommand(new Basic.Publish.Builder()
                                    .exchange(exchange)
                                    .routingKey(routingKey)
                                    .mandatory(mandatory)
                                    .immediate(immediate)
                                .build(),
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

    public void exchangeDeclareNoWait(String exchange,
                                      String type,
                                      boolean durable,
                                      boolean autoDelete,
                                      boolean internal,
                                      Map<String, Object> arguments) throws IOException {
        transmit(new AMQCommand(new Exchange.Declare.Builder()
                                .exchange(exchange)
                                .type(type)
                                .durable(durable)
                                .autoDelete(autoDelete)
                                .internal(internal)
                                .arguments(arguments)
                                .passive(false)
                                .nowait(true)
                                .build()));
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
                exnWrappingRpc(new Exchange.Declare.Builder()
                                .exchange(exchange)
                                .type(type)
                                .durable(durable)
                                .autoDelete(autoDelete)
                                .internal(internal)
                                .arguments(arguments)
                               .build())
                .getMethod();
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
            exnWrappingRpc(new Exchange.Declare.Builder()
                            .exchange(exchange)
                            .type("")
                            .passive()
                           .build())
            .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused)
        throws IOException
    {
        return (Exchange.DeleteOk)
            exnWrappingRpc(new Exchange.Delete.Builder()
                            .exchange(exchange)
                            .ifUnused(ifUnused)
                           .build())
            .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException {
        transmit(new AMQCommand(new Exchange.Delete.Builder()
                                        .exchange(exchange)
                                        .ifUnused(ifUnused)
                                        .nowait(true)
                                        .build()));
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
        return (Exchange.BindOk)
               exnWrappingRpc(new Exchange.Bind.Builder()
                               .destination(destination)
                               .source(source)
                               .routingKey(routingKey)
                               .arguments(arguments)
                              .build())
               .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public void exchangeBindNoWait(String destination,
                                   String source,
                                   String routingKey,
                                   Map<String, Object> arguments) throws IOException {
        transmit(new AMQCommand(new Exchange.Bind.Builder()
                                .destination(destination)
                                .source(source)
                                .routingKey(routingKey)
                                .arguments(arguments)
                                .nowait(true)
                                .build()));
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
        return (Exchange.UnbindOk)
               exnWrappingRpc(new Exchange.Unbind.Builder()
                               .destination(destination)
                               .source(source)
                               .routingKey(routingKey)
                               .arguments(arguments)
                              .build())
               .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Exchange.UnbindOk exchangeUnbind(String destination, String source,
            String routingKey) throws IOException {
        return exchangeUnbind(destination, source, routingKey, null);
    }

    /** Public API - {@inheritDoc} */
    public void exchangeUnbindNoWait(String destination, String source,
                                     String routingKey, Map<String, Object> arguments)
            throws IOException {
        transmit(new AMQCommand(new Exchange.Unbind.Builder()
                                 .destination(destination)
                                 .source(source)
                                 .routingKey(routingKey)
                                 .arguments(arguments)
                                 .nowait(true)
                                 .build()));
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive,
                                        boolean autoDelete, Map<String, Object> arguments)
        throws IOException
    {
        validateQueueNameLength(queue);
        return (Queue.DeclareOk)
               exnWrappingRpc(new Queue.Declare.Builder()
                               .queue(queue)
                               .durable(durable)
                               .exclusive(exclusive)
                               .autoDelete(autoDelete)
                               .arguments(arguments)
                              .build())
               .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare()
        throws IOException
    {
        return queueDeclare("", false, true, true, null);
    }

    /** Public API - {@inheritDoc} */
    public void queueDeclareNoWait(String queue,
                                   boolean durable,
                                   boolean exclusive,
                                   boolean autoDelete,
                                   Map<String, Object> arguments) throws IOException {
        validateQueueNameLength(queue);
        transmit(new AMQCommand(new Queue.Declare.Builder()
                                .queue(queue)
                                .durable(durable)
                                .exclusive(exclusive)
                                .autoDelete(autoDelete)
                                .arguments(arguments)
                                .passive(false)
                                .nowait(true)
                                .build()));
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeclareOk queueDeclarePassive(String queue)
        throws IOException
    {
        validateQueueNameLength(queue);
        return (Queue.DeclareOk)
               exnWrappingRpc(new Queue.Declare.Builder()
                               .queue(queue)
                               .passive()
                               .exclusive()
                               .autoDelete()
                              .build())
               .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty)
        throws IOException
    {
        validateQueueNameLength(queue);
        return (Queue.DeleteOk)
               exnWrappingRpc(new Queue.Delete.Builder()
                               .queue(queue)
                               .ifUnused(ifUnused)
                               .ifEmpty(ifEmpty)
                              .build())
               .getMethod();
    }

    @Override
    public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        validateQueueNameLength(queue);
        transmit(new AMQCommand(new Queue.Delete.Builder()
                                        .queue(queue)
                                        .ifUnused(ifUnused)
                                        .ifEmpty(ifEmpty)
                                        .nowait(true)
                                        .build()));
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
        validateQueueNameLength(queue);
        return (Queue.BindOk)
               exnWrappingRpc(new Queue.Bind.Builder()
                               .queue(queue)
                               .exchange(exchange)
                               .routingKey(routingKey)
                               .arguments(arguments)
                              .build())
               .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.BindOk queueBind(String queue, String exchange, String routingKey)
        throws IOException
    {
        return queueBind(queue, exchange, routingKey, null);
    }

    /** Public API - {@inheritDoc} */
    public void queueBindNoWait(String queue,
                                String exchange,
                                String routingKey,
                                Map<String, Object> arguments) throws IOException {
        validateQueueNameLength(queue);
        transmit(new AMQCommand(new Queue.Bind.Builder()
                                .queue(queue)
                                .exchange(exchange)
                                .routingKey(routingKey)
                                .arguments(arguments)
                                .build()));
    }

    /** Public API - {@inheritDoc} */
    public Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey,
                                      Map<String, Object> arguments)
        throws IOException
    {
        validateQueueNameLength(queue);
        return (Queue.UnbindOk)
               exnWrappingRpc(new Queue.Unbind.Builder()
                               .queue(queue)
                               .exchange(exchange)
                               .routingKey(routingKey)
                               .arguments(arguments)
                              .build())
               .getMethod();
    }

    /** Public API - {@inheritDoc} */
    public Queue.PurgeOk queuePurge(String queue)
        throws IOException
    {
        validateQueueNameLength(queue);
        return (Queue.PurgeOk)
               exnWrappingRpc(new Queue.Purge.Builder()
                               .queue(queue)
                              .build())
               .getMethod();
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
        validateQueueNameLength(queue);
        AMQCommand replyCommand = exnWrappingRpc(new Basic.Get.Builder()
                                                  .queue(queue)
                                                  .noAck(autoAck)
                                                 .build());
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
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
                               Consumer callback)
        throws IOException
    {
        return basicConsume(queue, autoAck, "", false, false, arguments, callback);
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

                dispatcher.handleConsumeOk(callback, actualConsumerTag);
                return actualConsumerTag;
            }
        };

        rpc(new Basic.Consume.Builder()
             .queue(queue)
             .consumerTag(consumerTag)
             .noLocal(noLocal)
             .noAck(autoAck)
             .exclusive(exclusive)
             .arguments(arguments)
            .build(),
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
        final Consumer originalConsumer = _consumers.get(consumerTag);
        if (originalConsumer == null)
            throw new IOException("Unknown consumerTag");
        BlockingRpcContinuation<Consumer> k = new BlockingRpcContinuation<Consumer>() {
            public Consumer transformReply(AMQCommand replyCommand) {
                replyCommand.getMethod();
                _consumers.remove(consumerTag); //may already have been removed
                dispatcher.handleCancelOk(originalConsumer, consumerTag);
                return originalConsumer;
            }
        };

        rpc(new Basic.Cancel(consumerTag, false), k);

        try {
            k.getReply(); // discard result
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
    @SuppressWarnings("deprecation")
    @Deprecated
    public boolean flowBlocked() {
        return _blockContent;
    }

    /** Public API - {@inheritDoc} */
    public long getNextPublishSeqNo() {
        return nextPublishSeqNo;
    }

    public void asyncRpc(Method method) throws IOException {
        transmit(method);
    }

    public AMQCommand rpc(Method method) throws IOException {
        return exnWrappingRpc(method);
    }

    @Override
    public void enqueueRpc(RpcContinuation k) {
        synchronized (_channelMutex) {
            super.enqueueRpc(k);
            dispatcher.setUnlimited(true);
        }
    }

    @Override
    protected void markRpcFinished() {
        synchronized (_channelMutex) {
            dispatcher.setUnlimited(false);
        }
    }

    private void handleAckNack(long seqNo, boolean multiple, boolean nack) {
        if (multiple) {
            unconfirmedSet.headSet(seqNo + 1).clear();
        } else {
            unconfirmedSet.remove(seqNo);
        }
        synchronized (unconfirmedSet) {
            onlyAcksReceived = onlyAcksReceived && !nack;
            if (unconfirmedSet.isEmpty())
                unconfirmedSet.notifyAll();
        }
    }

    private void validateQueueNameLength(String queue) {
        if(queue.length() > 255) {
           throw new IllegalArgumentException("queue name must be no more than 255 characters long");
        }
    }
}
