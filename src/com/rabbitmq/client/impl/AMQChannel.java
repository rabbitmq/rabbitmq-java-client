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

package com.rabbitmq.client.impl;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.BlockingValueOrException;

/**
 * Base class modelling an AMQ channel. Subclasses implement close()
 * and processAsync(), and may choose to override
 * processShutdownSignal().
 *
 * @see ChannelN
 * @see Connection
 */
public abstract class AMQChannel extends ShutdownNotifierComponent {
    /** The connection this channel is associated with. */
    public final AMQConnection _connection;

    /** This channel's channel number. */
    public final int _channelNumber;

    /** State machine assembling commands on their way in. */
    public AMQCommand.Assembler _commandAssembler = AMQCommand.newAssembler();

    /** The current outstanding RPC request, if any. (Could become a queue in future.) */
    public RpcContinuation _activeRpc = null;

    /** Whether transmission of content-bearing methods should be blocked */
    public boolean _blockContent = false;

    /**
     * Construct a channel on the given connection, with the given channel number.
     * @param connection the underlying connection for this channel
     * @param channelNumber the allocated reference number for this channel
     */
    public AMQChannel(AMQConnection connection, int channelNumber) {
        this._connection = connection;
        this._channelNumber = channelNumber;
    }

    /**
     * Public API - Retrieves this channel's channel number.
     * @return the channel number
     */
    public int getChannelNumber() {
        return _channelNumber;
    }

    /**
     * Public API - Retrieves this channel's underlying connection.
     * @return the connection
     */
    public Connection getConnection() {
        return _connection;
    }

    /**
     * Private API - When the Connection receives a Frame for this
     * channel, it passes it to this method.
     * @param frame the incoming frame
     * @throws IOException if an error is encountered
     */
    public void handleFrame(Frame frame) throws IOException {
        AMQCommand command = _commandAssembler.handleFrame(frame);
        if (command != null) { // a complete command has rolled off the assembly line
            _commandAssembler = AMQCommand.newAssembler(); // prepare for the next one
            handleCompleteInboundCommand(command);
        }
    }

    /**
     * Private API - handle a command which has been assembled
     * @throws IOException if there's any problem
     *
     * @param command the incoming command
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
            nextOutstandingRpc().handleCommand(command);
        }
    }

    public synchronized void enqueueRpc(RpcContinuation k)
    {
        if (_activeRpc != null) {
            throw new IllegalStateException("cannot execute more than one synchronous AMQP command at a time");
        }
        _activeRpc = k;
    }

    public synchronized RpcContinuation nextOutstandingRpc()
    {
        RpcContinuation result = _activeRpc;
        _activeRpc = null;
        return result;
    }

    public void ensureIsOpen()
        throws AlreadyClosedException
    {
        if (!isOpen()) {
            throw new AlreadyClosedException("Attempt to use closed channel", this);
        }
    }

    /**
     * Protected API - sends a Command to the broker and waits for the
     * next inbound Command from the broker: only for use from
     * non-connection-MainLoop threads!
     */
    public AMQCommand rpc(Method m) {
        SimpleBlockingRpcContinuation k = new SimpleBlockingRpcContinuation();
        rpc(m, k);
        // At this point, the request method has been sent, and we
        // should wait for the reply to arrive.
        //
        // Calling getReply() on the continuation puts us to sleep
        // until the connection's reader-thread throws the reply over
        // the fence.
        return k.getReply();
    }

    public synchronized void rpc(Method m, RpcContinuation k) {
        ensureIsOpen();
        quiescingRpc(m, k);
    }
    
    public synchronized void quiescingRpc(Method m, RpcContinuation k) {
        enqueueRpc(k);
        quiescingTransmit(m);
    }

    /**
     * Protected API - called by nextCommand to check possibly handle an incoming Command before it is returned to the caller of nextCommand. If this method
     * returns true, the command is considered handled and is not passed back to nextCommand's caller; if it returns false, nextCommand returns the command as
     * usual. This is used in subclasses to implement handling of Basic.Return and Basic.Deliver messages, as well as Channel.Close and Connection.Close.
     * @param command the command to handle asynchronously
     * @return true if we handled the command; otherwise the caller should consider it "unhandled"
     */
    public abstract boolean processAsync(Command command) throws IOException;

    @Override public String toString() {
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
            synchronized (this) {
                if (!ignoreClosed)
                    ensureIsOpen(); // invariant: we should never be shut down more than once per instance
                if (isOpen())
                    _shutdownCause = signal;
                
                notifyAll();
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

    public synchronized void transmit(Method m) {
        transmit(new AMQCommand(m));
    }

    public synchronized void transmit(AMQCommand c) {
        ensureIsOpen();
        quiescingTransmit(c);
    }

    public synchronized void quiescingTransmit(Method m) {
        quiescingTransmit(new AMQCommand(m));
    }

    public synchronized void quiescingTransmit(AMQCommand c) {
        if (c.getMethod().hasContent()) {
            while (_blockContent) {
                try {
                    wait();
                } catch (InterruptedException e) {}
                
                // This is to catch a situation when the thread wakes up during
                // shutdown. Currently, no command that has content is allowed
                // to send anything in a closing state.
                ensureIsOpen();
            }
        }
        c.transmit(this);
    }

    public AMQConnection getAMQConnection() {
        return _connection;
    }

    public interface RpcContinuation {
        void handleCommand(AMQCommand command);
        void handleShutdownSignal(ShutdownSignalException signal);
    }

    public static abstract class BlockingRpcContinuation<T> implements RpcContinuation {
        public final BlockingValueOrException<T, ShutdownSignalException> _blocker =
            new BlockingValueOrException<T, ShutdownSignalException>();

        public void handleCommand(AMQCommand command) {
            _blocker.setValue(transformReply(command));
        }

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
        public AMQCommand transformReply(AMQCommand command) {
            return command;
        }
    }
}
