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
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.Basic;
import com.rabbitmq.client.AMQP.Confirm;
import com.rabbitmq.client.AMQP.Exchange;
import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.AMQP.Tx;
import com.rabbitmq.client.Method;
import com.rabbitmq.utility.BlockingValueOrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class modelling an AMQ channel. Subclasses implement
 * {@link com.rabbitmq.client.Channel#close} and
 * {@link #processAsync processAsync()}, and may choose to override
 * {@link #processShutdownSignal processShutdownSignal()} and
 * {@link #rpc rpc()}.
 *
 * @see ChannelN
 * @see Connection
 */
public abstract class AMQChannel extends ShutdownNotifierComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AMQChannel.class);

    protected static final int NO_RPC_TIMEOUT = 0;

    /**
     * Protected; used instead of synchronizing on the channel itself,
     * so that clients can themselves use the channel to synchronize
     * on.
     */
    protected final Object _channelMutex = new Object();

    /** The connection this channel is associated with. */
    private final AMQConnection _connection;

    /** This channel's channel number. */
    private final int _channelNumber;

    /** Command being assembled */
    private AMQCommand _command = new AMQCommand();

    /** The current outstanding RPC request, if any. (Could become a queue in future.) */
    private RpcContinuation _activeRpc = null;

    /** Whether transmission of content-bearing methods should be blocked */
    public volatile boolean _blockContent = false;

    /** Timeout for RPC calls */
    protected final int _rpcTimeout;

    /**
     * Construct a channel on the given connection, with the given channel number.
     * @param connection the underlying connection for this channel
     * @param channelNumber the allocated reference number for this channel
     */
    public AMQChannel(AMQConnection connection, int channelNumber) {
        this._connection = connection;
        this._channelNumber = channelNumber;
        if(connection.getChannelRpcTimeout() < 0) {
            throw new IllegalArgumentException("Continuation timeout on RPC calls cannot be less than 0");
        }
        this._rpcTimeout = connection.getChannelRpcTimeout();
    }

    /**
     * Public API - Retrieves this channel's channel number.
     * @return the channel number
     */
    public int getChannelNumber() {
        return _channelNumber;
    }

    /**
     * Private API - When the Connection receives a Frame for this
     * channel, it passes it to this method.
     * @param frame the incoming frame
     * @throws IOException if an error is encountered
     */
    public void handleFrame(Frame frame) throws IOException {
        AMQCommand command = _command;
        if (command.handleFrame(frame)) { // a complete command has rolled off the assembly line
            _command = new AMQCommand(); // prepare for the next one
            handleCompleteInboundCommand(command);
        }
    }

    /**
     * Placeholder until we address bug 15786 (implementing a proper exception hierarchy).
     * In the meantime, this at least won't throw away any information from the wrapped exception.
     * @param ex the exception to wrap
     * @return the wrapped exception
     */
    public static IOException wrap(ShutdownSignalException ex) {
        return wrap(ex, null);
    }

    public static IOException wrap(ShutdownSignalException ex, String message) {
        IOException ioe = new IOException(message);
        ioe.initCause(ex);
        return ioe;
    }

    /**
     * Placeholder until we address bug 15786 (implementing a proper exception hierarchy).
     */
    public AMQCommand exnWrappingRpc(Method m)
        throws IOException
    {
        try {
            return privateRpc(m);
        } catch (AlreadyClosedException ace) {
            // Do not wrap it since it means that connection/channel
            // was closed in some action in the past
            throw ace;
        } catch (ShutdownSignalException ex) {
            throw wrap(ex);
        }
    }

    /**
     * Private API - handle a command which has been assembled
     * @throws IOException if there's any problem
     *
     * @param command the incoming command
     * @throws IOException
     */
    public void handleCompleteInboundCommand(AMQCommand command) throws IOException {
        // First, offer the command to the asynchronous-command
        // handling mechanism, which gets to act as a filter on the
        // incoming command stream.  If processAsync() returns true,
        // the command has been dealt with by the filter and so should
        // not be processed further.  It will return true for
        // asynchronous commands (deliveries/returns/other events),
        // and false for commands that should be passed on to some
        // waiting RPC continuation.
        if (!processAsync(command)) {
            // The filter decided not to handle/consume the command,
            // so it must be some reply to an earlier RPC.
            final RpcContinuation nextOutstandingRpc;
            synchronized (_channelMutex) {
                // check if this reply command is intended for the current waiting request before calling nextOutstandingRpc()
                if (_activeRpc instanceof RpcContinuation2) {
                    if (!((RpcContinuation2)_activeRpc).canHandleCommand(command)) {
                        // this reply command is not intended for the current waiting request
                        // most likely a previous request timed out and this command is the reply for that.
                        // Throw this reply command away so we don't stop the current request from waiting for its reply
                        return;
                    }
                }
                nextOutstandingRpc = nextOutstandingRpc();
            }
            // the outstanding RPC can be null when calling Channel#asyncRpc
            if (nextOutstandingRpc != null) {
                nextOutstandingRpc.handleCommand(command);
                markRpcFinished();
            }
        }
    }

    public void enqueueRpc(RpcContinuation k)
    {
        synchronized (_channelMutex) {
            boolean waitClearedInterruptStatus = false;
            while (_activeRpc != null) {
                try {
                    _channelMutex.wait();
                } catch (InterruptedException e) {
                    waitClearedInterruptStatus = true;
                }
            }
            if (waitClearedInterruptStatus) {
                Thread.currentThread().interrupt();
            }
            _activeRpc = k;
        }
    }

    public boolean isOutstandingRpc()
    {
        synchronized (_channelMutex) {
            return (_activeRpc != null);
        }
    }

    public RpcContinuation nextOutstandingRpc()
    {
        synchronized (_channelMutex) {
            RpcContinuation result = _activeRpc;
            _activeRpc = null;
            _channelMutex.notifyAll();
            return result;
        }
    }

    protected void markRpcFinished() {
        // no-op
    }

    public void ensureIsOpen()
        throws AlreadyClosedException
    {
        if (!isOpen()) {
            throw new AlreadyClosedException(getCloseReason());
        }
    }

    /**
     * Protected API - sends a {@link Method} to the broker and waits for the
     * next in-bound Command from the broker: only for use from
     * non-connection-MainLoop threads!
     */
    public AMQCommand rpc(Method m)
        throws IOException, ShutdownSignalException
    {
        return privateRpc(m);
    }

    public AMQCommand rpc(Method m, int timeout)
            throws IOException, ShutdownSignalException, TimeoutException {
        return privateRpc(m, timeout);
    }

    private AMQCommand privateRpc(Method m)
        throws IOException, ShutdownSignalException
    {
        SimpleBlockingRpcContinuation k = new SimpleBlockingRpcContinuation(m);
        rpc(m, k);
        // At this point, the request method has been sent, and we
        // should wait for the reply to arrive.
        //
        // Calling getReply() on the continuation puts us to sleep
        // until the connection's reader-thread throws the reply over
        // the fence or the RPC times out (if enabled)
        if(_rpcTimeout == NO_RPC_TIMEOUT) {
            return k.getReply();
        } else {
            try {
                return k.getReply(_rpcTimeout);
            } catch (TimeoutException e) {
                throw wrapTimeoutException(m, e);
            }
        }
    }
    
    private void cleanRpcChannelState() {
        try {
            // clean RPC channel state
            nextOutstandingRpc();
            markRpcFinished();
        } catch (Exception ex) {
            LOGGER.warn("Error while cleaning timed out channel RPC: {}", ex.getMessage());
        }
    }
    
    /** Cleans RPC channel state after a timeout and wraps the TimeoutException in a ChannelContinuationTimeoutException */
    protected ChannelContinuationTimeoutException wrapTimeoutException(final Method m, final TimeoutException e)  {
        cleanRpcChannelState();
        return new ChannelContinuationTimeoutException(e, this, this._channelNumber, m);
    }

    private AMQCommand privateRpc(Method m, int timeout)
            throws IOException, ShutdownSignalException, TimeoutException {
        SimpleBlockingRpcContinuation k = new SimpleBlockingRpcContinuation(m);
        rpc(m, k);

        try {
            return k.getReply(timeout);
        } catch (TimeoutException e) {
            cleanRpcChannelState();
            throw e;
        }
    }

    public void rpc(Method m, RpcContinuation k)
        throws IOException
    {
        synchronized (_channelMutex) {
            ensureIsOpen();
            quiescingRpc(m, k);
        }
    }

    public void quiescingRpc(Method m, RpcContinuation k)
        throws IOException
    {
        synchronized (_channelMutex) {
            enqueueRpc(k);
            quiescingTransmit(m);
        }
    }

    /**
     * Protected API - called by nextCommand to check possibly handle an incoming Command before it is returned to the caller of nextCommand. If this method
     * returns true, the command is considered handled and is not passed back to nextCommand's caller; if it returns false, nextCommand returns the command as
     * usual. This is used in subclasses to implement handling of Basic.Return and Basic.Deliver messages, as well as Channel.Close and Connection.Close.
     * @param command the command to handle asynchronously
     * @return true if we handled the command; otherwise the caller should consider it "unhandled"
     */
    public abstract boolean processAsync(Command command) throws IOException;

    @Override
    public String toString() {
        return "AMQChannel(" + _connection + "," + _channelNumber + ")";
    }

    /**
     * Protected API - respond, in the driver thread, to a {@link ShutdownSignalException}.
     * @param signal the signal to handle
     * @param ignoreClosed the flag indicating whether to ignore the AlreadyClosedException
     *                     thrown when the channel is already closed
     * @param notifyRpc the flag indicating whether any remaining rpc continuation should be
     *                  notified with the given signal
     */
    public void processShutdownSignal(ShutdownSignalException signal,
                                      boolean ignoreClosed,
                                      boolean notifyRpc) {
        try {
            synchronized (_channelMutex) {
                if (!setShutdownCauseIfOpen(signal)) {
                    if (!ignoreClosed)
                        throw new AlreadyClosedException(getCloseReason());
                }

                _channelMutex.notifyAll();
            }
        } finally {
            if (notifyRpc)
                notifyOutstandingRpc(signal);
        }
    }

    public void notifyOutstandingRpc(ShutdownSignalException signal) {
        RpcContinuation k = nextOutstandingRpc();
        if (k != null) {
            k.handleShutdownSignal(signal);
        }
    }

    public void transmit(Method m) throws IOException {
        synchronized (_channelMutex) {
            transmit(new AMQCommand(m));
        }
    }

    public void transmit(AMQCommand c) throws IOException {
        synchronized (_channelMutex) {
            ensureIsOpen();
            quiescingTransmit(c);
        }
    }

    public void quiescingTransmit(Method m) throws IOException {
        synchronized (_channelMutex) {
            quiescingTransmit(new AMQCommand(m));
        }
    }

    public void quiescingTransmit(AMQCommand c) throws IOException {
        synchronized (_channelMutex) {
            if (c.getMethod().hasContent()) {
                while (_blockContent) {
                    try {
                        _channelMutex.wait();
                    } catch (InterruptedException ignored) {}

                    // This is to catch a situation when the thread wakes up during
                    // shutdown. Currently, no command that has content is allowed
                    // to send anything in a closing state.
                    ensureIsOpen();
                }
            }
            c.transmit(this);
        }
    }

    public AMQConnection getConnection() {
        return _connection;
    }

    public interface RpcContinuation {
        void handleCommand(AMQCommand command);
        void handleShutdownSignal(ShutdownSignalException signal);
    }
    
    public interface RpcContinuation2 extends RpcContinuation {
        /** @return true if the reply command can be handled for this request */
        boolean canHandleCommand(AMQCommand command);
    }

    public static abstract class BlockingRpcContinuation<T> implements RpcContinuation2 {
        public final BlockingValueOrException<T, ShutdownSignalException> _blocker =
            new BlockingValueOrException<T, ShutdownSignalException>();
        protected final Method request;
        
        public BlockingRpcContinuation() {
            request = null;
        }
        
        public BlockingRpcContinuation(final Method request) {
            this.request = request;
        }
        
        @Override
        public boolean canHandleCommand(AMQCommand command) {
            // make a best effort attempt to ensure the reply was intended for this rpc request
            // Ideally each rpc request would tag an id on it that could be returned and referenced on its reply.
            // But because that would be a very large undertaking to add passively this logic at least protects against ClassCastExceptions
            if (request != null) {
                final Method reply = command.getMethod();
                if (request instanceof Basic.Qos) {
                    return reply instanceof Basic.QosOk;
                } else if (request instanceof Basic.Get) {
                    return reply instanceof Basic.GetOk || reply instanceof Basic.GetEmpty;
                } else if (request instanceof Basic.Consume) {
                    if (!(reply instanceof Basic.ConsumeOk))
                        return false;
                    // can also check the consumer tags match here. handle case where request consumer tag is empty and server-generated.
                    final String consumerTag = ((Basic.Consume)request).getConsumerTag();
                    return consumerTag == null || consumerTag.equals("") || consumerTag.equals(((Basic.ConsumeOk)reply).getConsumerTag());
                } else if (request instanceof Basic.Cancel) {
                    if (!(reply instanceof Basic.CancelOk))
                        return false;
                    // can also check the consumer tags match here
                    return ((Basic.Cancel)request).getConsumerTag().equals(((Basic.CancelOk)reply).getConsumerTag());
                } else if (request instanceof Basic.Recover) {
                    return reply instanceof Basic.RecoverOk;
                } else if (request instanceof Exchange.Declare) {
                    return reply instanceof Exchange.DeclareOk;
                } else if (request instanceof Exchange.Delete) {
                    return reply instanceof Exchange.DeleteOk;
                } else if (request instanceof Exchange.Bind) {
                    return reply instanceof Exchange.BindOk;
                } else if (request instanceof Exchange.Unbind) {
                    return reply instanceof Exchange.UnbindOk;
                } else if (request instanceof Queue.Declare) {
                    if (!(reply instanceof Queue.DeclareOk))
                        return false;
                    // can also check the queue names match here. handle case where request queue name is empty and server-generated.
                    final String queue = ((Queue.Declare)request).getQueue();
                    return queue == null || queue.equals("") || queue.equals(((Queue.DeclareOk)reply).getQueue());
                } else if (request instanceof Queue.Delete) {
                    return reply instanceof Queue.DeleteOk;
                } else if (request instanceof Queue.Bind) {
                    return reply instanceof Queue.BindOk;
                } else if (request instanceof Queue.Unbind) {
                    return reply instanceof Queue.UnbindOk;
                } else if (request instanceof Queue.Purge) {
                    return reply instanceof Queue.PurgeOk;
                } else if (request instanceof Tx.Select) {
                    return reply instanceof Tx.SelectOk;
                } else if (request instanceof Tx.Commit) {
                    return reply instanceof Tx.CommitOk;
                } else if (request instanceof Tx.Rollback) {
                    return reply instanceof Tx.RollbackOk;
                } else if (request instanceof Confirm.Select) {
                    return reply instanceof Confirm.SelectOk;
                }
            }
            // for passivity default to true
            return true;
        }
        
        @Override
        public void handleCommand(AMQCommand command) {
            _blocker.setValue(transformReply(command));
        }

        @Override
        public void handleShutdownSignal(ShutdownSignalException signal) {
            _blocker.setException(signal);
        }

        public T getReply() throws ShutdownSignalException
        {
            return _blocker.uninterruptibleGetValue();
        }

        public T getReply(int timeout)
            throws ShutdownSignalException, TimeoutException
        {
            return _blocker.uninterruptibleGetValue(timeout);
        }

        public abstract T transformReply(AMQCommand command);
    }

    public static class SimpleBlockingRpcContinuation
        extends BlockingRpcContinuation<AMQCommand>
    {
        public SimpleBlockingRpcContinuation() {
            super();
        }
        
        public SimpleBlockingRpcContinuation(final Method request) {
            super(request);
        }
        
        @Override
        public AMQCommand transformReply(AMQCommand command) {
            return command;
        }
    }
}
