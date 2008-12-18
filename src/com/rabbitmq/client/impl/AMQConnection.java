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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.MissedHeartbeatException;
import com.rabbitmq.client.RedirectException;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.BlockingCell;
import com.rabbitmq.utility.Utility;

/**
 * Concrete class representing and managing an AMQP connection to a broker.
 * <p>
 * To connect to a broker,
 *
 * <pre>
 * AMQConnection conn = new AMQConnection(hostName, portNumber);
 * conn.open(userName, portNumber, virtualHost);
 * </pre>
 *
 * <pre>
 * ChannelN ch1 = conn.createChannel(1);
 * ch1.open(&quot;&quot;);
 * </pre>
 */
public class AMQConnection extends ShutdownNotifierComponent implements Connection {
    /** Timeout used while waiting for AMQP handshaking to complete (milliseconds) */
    public static final int HANDSHAKE_TIMEOUT = 10000;

    /** Timeout used while waiting for a connection.close-ok (milliseconds) */
    public static final int CONNECTION_CLOSING_TIMEOUT = 10000;

    private static final Version clientVersion =
        new Version(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR);

    /** Initialization parameters */
    public final ConnectionParameters _params;

    /** The special channel 0 */
    public final AMQChannel _channel0 = new AMQChannel(this, 0) {
            @Override public boolean processAsync(Command c) throws IOException {
                return _connection.processControlCommand(c);
            }
        };

    /** Object that manages a set of channels */
    public final ChannelManager _channelManager = new ChannelManager();

    /** Frame source/sink */
    public final FrameHandler _frameHandler;

    /** Flag controlling the main driver loop's termination */
    public volatile boolean _running = false;

    /** Maximum frame length, or zero if no limit is set */
    public int _frameMax;

    /** Handler for (otherwise-unhandled) exceptions that crop up in the mainloop. */
    public final ExceptionHandler _exceptionHandler;

    /**
     * Object used for blocking main application thread when doing all the necessary
     * connection shutdown operations
     */
    public BlockingCell<Object> _appContinuation = new BlockingCell<Object>();

    /** Flag indicating whether the client received Connection.Close message from the broker */
    public boolean _brokerInitiatedShutdown = false;
    
    /**
     * Protected API - respond, in the driver thread, to a ShutdownSignal.
     * @param channelNumber the number of the channel to disconnect
     */
    public final void disconnectChannel(int channelNumber) {
        _channelManager.disconnectChannel(channelNumber);
    }

    public void ensureIsOpen()
        throws AlreadyClosedException
    {
        if (!isOpen()) {
            throw new AlreadyClosedException("Attempt to use closed connection", this);
        }
    }

    /**
     * Timestamp of last time we wrote a frame - used for deciding when to
     * send a heartbeat
     */
    public volatile long _lastActivityTime = Long.MAX_VALUE;

    /**
     * Count of socket-timeouts that have happened without any incoming frames
     */
    public int _missedHeartbeats;

    /**
     * Currently-configured heartbeat interval, in seconds. 0 meaning none.
     */
    public int _heartbeat;

    /** Hosts retrieved from the connection.open-ok */
    public Address[] _knownHosts;

    public String getHost() {
        return _frameHandler.getHost();
    }

    public int getPort() {
        return _frameHandler.getPort();
    }

    public ConnectionParameters getParameters() {
        return _params;
    }

    public Address[] getKnownHosts() {
        return _knownHosts;
    }

    /**
     * Construct a new connection to a broker.
     * @param params the initialization parameters for a connection
     * @param insist true if broker redirects are disallowed
     * @param frameHandler interface to an object that will handle the frame I/O for this connection
     * @throws RedirectException if the server is redirecting us to a different host/port
     * @throws java.io.IOException if an error is encountered
     */
    public AMQConnection(ConnectionParameters params,
                         boolean insist,
                         FrameHandler frameHandler) throws RedirectException, IOException {
        this(params, insist, frameHandler, new DefaultExceptionHandler());
    }

    /**
     * Construct a new connection to a broker.
     * @param params the initialization parameters for a connection
     * @param insist true if broker redirects are disallowed
     * @param frameHandler interface to an object that will handle the frame I/O for this connection
     * @param exceptionHandler interface to an object that will handle any special exceptions encountered while using this connection
     * @throws RedirectException if the server is redirecting us to a different host/port
     * @throws java.io.IOException if an error is encountered
     */
    public AMQConnection(ConnectionParameters params,
                         boolean insist,
                         FrameHandler frameHandler,
                         ExceptionHandler exceptionHandler)
        throws RedirectException, IOException
    {
        checkPreconditions();
        _params = params;
        _frameHandler = frameHandler;
        _running = true;
        _frameMax = 0;
        _missedHeartbeats = 0;
        _heartbeat = 0;
        _exceptionHandler = exceptionHandler;
        _brokerInitiatedShutdown = false;

        new MainLoop(); // start the main loop going

        _knownHosts = open(_params, insist);
    }

    /**
     * Private API - check required preconditions and protocol invariants
     */
    public void checkPreconditions() {
        AMQCommand.checkEmptyContentBodyFrameSize();
    }

    /**
     * @see com.rabbitmq.client.Connection#getChannelMax()
     */
    public int getChannelMax() {
        return _channelManager.getChannelMax();
    }

    /**
     * Protected API - set the max <b>number</b> of channels available
     */
    public void setChannelMax(int value) {
        _channelManager.setChannelMax(value);
    }

    /**
     * @see com.rabbitmq.client.Connection#getFrameMax()
     */
    public int getFrameMax() {
        return _frameMax;
    }

    /**
     * Protected API - set the max frame size. Should only be called during
     * tuning.
     */
    public void setFrameMax(int value) {
        _frameMax = value;
    }

    /**
     * @see com.rabbitmq.client.Connection#getHeartbeat()
     */
    public int getHeartbeat() {
        return _heartbeat;
    }

    /**
     * Protected API - set the heartbeat timeout. Should only be called
     * during tuning.
     */
    public void setHeartbeat(int heartbeat) {
        try {
            // Divide by four to make the maximum unwanted delay in
            // sending a timeout be less than a quarter of the
            // timeout setting.
            _heartbeat = heartbeat;
            _frameHandler.setTimeout(heartbeat * 1000 / 4);
        } catch (SocketException se) {
            // should do more here?
        }
    }

    /**
     * Protected API - retrieve the current ExceptionHandler
     */
    public ExceptionHandler getExceptionHandler() {
        return _exceptionHandler;
    }

    /**
     * Public API - creates a new channel using the specified channel number.
     */

    public Channel createChannel(int channelNumber) throws IOException {
        ensureIsOpen();
        return _channelManager.createChannel(this, channelNumber);
    }

    /**
     * Public API - creates a new channel using an internally allocated channel number.
     */
    public Channel createChannel() throws IOException {
        ensureIsOpen();
        return _channelManager.createChannel(this);
    }

    /**
     * Private API - reads a single frame from the connection to the broker,
     * or returns null if the read times out.
     */
    public Frame readFrame() throws IOException {
        return _frameHandler.readFrame();
    }

    /**
     * Public API - sends a frame directly to the broker.
     */
    public void writeFrame(Frame f) throws IOException {
        _frameHandler.writeFrame(f);
        _lastActivityTime = System.nanoTime();
    }

    public Map<String, Object> buildClientPropertiesTable() {
        return Frame.buildTable(new Object[] {
            "product", LongStringHelper.asLongString("RabbitMQ"),
            "version", LongStringHelper.asLongString(ClientVersion.VERSION),
            "platform", LongStringHelper.asLongString("Java"),
            "copyright", LongStringHelper.asLongString("Copyright (C) 2007-2008 LShift Ltd., " +
                                                       "Cohesive Financial Technologies LLC., " +
                                                       "and Rabbit Technologies Ltd."),
            "information", LongStringHelper.asLongString("Licensed under the MPL.  " +
                                                         "See http://www.rabbitmq.com/")
        });
    }

    /**
     * Called by the connection's constructor. Sends the protocol
     * version negotiation header, and runs through
     * Connection.Start/.StartOk, Connection.Tune/.TuneOk, and then
     * calls Connection.Open and waits for the OpenOk. Sets heartbeat
     * and frame max values after tuning has taken place.
     * @param params the construction parameters for a Connection
     * @return the known hosts that came back in the connection.open-ok
     * @throws RedirectException if the server asks us to redirect to
     *                           a different host/port.
     * @throws java.io.IOException if any other I/O error occurs
     */
    public Address[] open(final ConnectionParameters params, boolean insist)
        throws RedirectException, IOException
    {
        try {
            AMQChannel.SimpleBlockingRpcContinuation connStartBlocker =
                new AMQChannel.SimpleBlockingRpcContinuation();
            // We enqueue an RPC continuation here without sending an RPC
            // request, since the protocol specifies that after sending
            // the version negotiation header, the client (connection
            // initiator) is to wait for a connection.start method to
            // arrive.
            _channel0.enqueueRpc(connStartBlocker);
            // The following two lines are akin to AMQChannel's
            // transmit() method for this pseudo-RPC.
            _frameHandler.setTimeout(HANDSHAKE_TIMEOUT);
            _frameHandler.sendHeader();

            // See bug 17389. The MainLoop could have shut down already in
            // which case we don't want to wait forever for a reply.

            // There is no race if the MainLoop shuts down after enqueuing
            // the RPC because if that happens the channel will correctly
            // pass the exception into RPC, waking it up.
            ensureIsOpen();

            AMQP.Connection.Start connStart =
                (AMQP.Connection.Start) connStartBlocker.getReply().getMethod();

            Version serverVersion =
                new Version(connStart.getVersionMajor(),
                            connStart.getVersionMinor());

            if (!Version.checkVersion(clientVersion, serverVersion)) {
                _frameHandler.close(); //this will cause mainLoop to terminate
                //TODO: throw a more specific exception
                throw new IOException("protocol version mismatch: expected " +
                                      clientVersion + ", got " + serverVersion);
            }
        } catch (ShutdownSignalException sse) {
            throw AMQChannel.wrap(sse);
        }

        LongString saslResponse = LongStringHelper.asLongString("\0" + params.getUserName() +
                                                                "\0" + params.getPassword());
        AMQImpl.Connection.StartOk startOk =
            new AMQImpl.Connection.StartOk(buildClientPropertiesTable(),
                                           "PLAIN",
                                           saslResponse,
                                           "en_US");

        AMQP.Connection.Tune connTune =
            (AMQP.Connection.Tune) _channel0.exnWrappingRpc(startOk).getMethod();

        int channelMax =
            negotiatedMaxValue(getParameters().getRequestedChannelMax(),
                               connTune.getChannelMax());
        setChannelMax(channelMax);

        int frameMax =
            negotiatedMaxValue(getParameters().getRequestedFrameMax(),
                               connTune.getFrameMax());
        setFrameMax(frameMax);

        int heartbeat =
            negotiatedMaxValue(getParameters().getRequestedHeartbeat(),
                               connTune.getHeartbeat());
        setHeartbeat(heartbeat);

        _channel0.transmit(new AMQImpl.Connection.TuneOk(channelMax,
                                                         frameMax,
                                                         heartbeat));

        Method res = _channel0.exnWrappingRpc(new AMQImpl.Connection.Open(params.getVirtualHost(),
                                                                          "",
                                                                          insist)).getMethod();
        if (res instanceof AMQP.Connection.Redirect) {
            AMQP.Connection.Redirect redirect = (AMQP.Connection.Redirect) res;
            throw new RedirectException(Address.parseAddress(redirect.getHost()),
                                        Address.parseAddresses(redirect.getKnownHosts()));
        } else {
            AMQP.Connection.OpenOk openOk = (AMQP.Connection.OpenOk) res;
            return Address.parseAddresses(openOk.getKnownHosts());
        }
    }

    private static int negotiatedMaxValue(int clientValue, int serverValue) {
        return (clientValue == 0 || serverValue == 0) ?
            Math.max(clientValue, serverValue) :
            Math.min(clientValue, serverValue);
    }

    private class MainLoop extends Thread {

        /** Start the main loop going. */
        public MainLoop() {
            start();
        }

        /**
         * Channel reader thread main loop. Reads a frame, and if it is
         * not a heartbeat frame, dispatches it to the channel it refers to.
         * Continues running until the "running" flag is set false by
         * shutdown().
         */
        @Override public void run() {
            try {
                while (_running) {
                    Frame frame = readFrame();
                    maybeSendHeartbeat();
                    if (frame != null) {
                        _missedHeartbeats = 0;
                        if (frame.type == AMQP.FRAME_HEARTBEAT) {
                            // Ignore it: we've already just reset the heartbeat counter.
                        } else {
                            if (frame.channel == 0) { // the special channel
                                _channel0.handleFrame(frame);
                            } else {
                                if (isOpen()) {
                                    // If we're still _running, but not isOpen(), then we
                                    // must be quiescing, which means any inbound frames
                                    // for non-zero channels (and any inbound commands on
                                    // channel zero that aren't Connection.CloseOk) must
                                    // be discarded.
                                    ChannelN channel = _channelManager.getChannel(frame.channel);
                                    // FIXME: catch NullPointerException and throw more informative one?
                                    channel.handleFrame(frame);
                                }
                            }
                        }
                    } else {
                        // Socket timeout waiting for a frame.
                        // Maybe missed heartbeat.
                        handleSocketTimeout();
                    }
                }
            } catch (EOFException ex) {
                if (!_brokerInitiatedShutdown)
                    shutdown(ex, false, ex, true);
            } catch (Throwable ex) {
                _exceptionHandler.handleUnexpectedConnectionDriverException(AMQConnection.this,
                                                                            ex);
                shutdown(ex, false, ex, true);
            } finally {
                // Finally, shut down our underlying data connection.
                _frameHandler.close();
                _appContinuation.set(null);
                notifyListeners();
            }
        }
    }

    private static final long NANOS_IN_SECOND = 1000 * 1000 * 1000;

    /**
     * Private API - Checks lastActivityTime and heartbeat, sending a
     * heartbeat frame if conditions are right.
     */
    public void maybeSendHeartbeat() throws IOException {
        if (_heartbeat == 0) {
            // No heartbeating.
            return;
        }

        long now = System.nanoTime();
        if (now > (_lastActivityTime + (_heartbeat * NANOS_IN_SECOND))) {
            _lastActivityTime = now;
            writeFrame(new Frame(AMQP.FRAME_HEARTBEAT, 0));
        }
    }

    /**
     * Private API - Called when a frame-read operation times out. Checks to
     * see if too many heartbeats have been missed, and if so, throws
     * MissedHeartbeatException.
     *
     * @throws MissedHeartbeatException
     *                 if too many silent timeouts have gone by
     */
    public void handleSocketTimeout() throws MissedHeartbeatException {
        if (_heartbeat == 0) {
            // No heartbeating. Go back and wait some more.
            return;
        }

        _missedHeartbeats++;

        // We check against 8 = 2 * 4 because we need to wait for at
        // least two complete heartbeat setting intervals before
        // complaining, and we've set the socket timeout to a quarter
        // of the heartbeat setting in setHeartbeat above.
        if (_missedHeartbeats > (2 * 4)) {
            throw new MissedHeartbeatException("Heartbeat missing with heartbeat == " +
                                               _heartbeat + " seconds");
        }
    }

    /**
     * Handles incoming control commands on channel zero.
     */
    public boolean processControlCommand(Command c)
        throws IOException
    {
        // Similar trick to ChannelN.processAsync used here, except
        // we're interested in whole-connection quiescing.

        // See the detailed comments in ChannelN.processAsync.

        Method method = c.getMethod();
        
        if (method instanceof AMQP.Connection.Close) {
            handleConnectionClose(c);
            return true;
        } else {
            if (isOpen()) {
                // Normal command.
                return false;
            } else {
                // Quiescing.
                if (method instanceof AMQP.Connection.CloseOk) {
                    // It's our final "RPC".
                    return false;
                } else {
                    // Ignore all others.
                    return true;
                }
            }
        }
    }

    public void handleConnectionClose(Command closeCommand) {
        ShutdownSignalException sse = shutdown(closeCommand, false, null, false);
        try {
            _channel0.quiescingTransmit(new AMQImpl.Connection.CloseOk());
        } catch (IOException ioe) {
            Utility.emptyStatement();
        }
        _heartbeat = 0; // Do not try to send heartbeats after CloseOk
        _brokerInitiatedShutdown = true;
        new SocketCloseWait(sse);
    }
    
    private class SocketCloseWait extends Thread {
        private ShutdownSignalException cause;
        
        public SocketCloseWait(ShutdownSignalException sse) {
            cause = sse;
            start();
        }
        
        @Override public void run() {
            try {
                _appContinuation.uninterruptibleGet(CONNECTION_CLOSING_TIMEOUT);
            } catch (TimeoutException ise) {
                // Broker didn't close socket on time, force socket close
                // FIXME: notify about timeout exception?
                _frameHandler.close();
            } finally {
                _running = false;
                _channel0.notifyOutstandingRpc(cause);
            }
        }
    }

    /**
     * Protected API - causes all attached channels to terminate with
     * a ShutdownSignal built from the argument, and stops this
     * connection from accepting further work from the application.
     * 
     * @return a shutdown signal built using the given arguments
     */
    public ShutdownSignalException shutdown(Object reason,
                         boolean initiatedByApplication,
                         Throwable cause,
                         boolean notifyRpc)
    {
        ShutdownSignalException sse = new ShutdownSignalException(true,initiatedByApplication,
                                                                  reason, this);
        sse.initCause(cause);
        synchronized (this) {
            if (initiatedByApplication)
                ensureIsOpen(); // invariant: we should never be shut down more than once per instance
            if (isOpen())
                _shutdownCause = sse;
        }
        _channel0.processShutdownSignal(sse, !initiatedByApplication, notifyRpc);
        _channelManager.handleSignal(sse);
        return sse;
    }

    public void close()
        throws IOException
    {
        close(-1);
    }

    public void close(int timeout)
        throws IOException
    {
        close(AMQP.REPLY_SUCCESS, "OK", timeout);
    }

    public void close(int closeCode, String closeMessage)
        throws IOException
    {
        close(closeCode, closeMessage, -1);
    }

    public void close(int closeCode, String closeMessage, int timeout)
        throws IOException
    {
        close(closeCode, closeMessage, true, null, timeout, false);
    }

    public void abort()
    {
        abort(-1);
    }

    public void abort(int closeCode, String closeMessage)
    {
       abort(closeCode, closeMessage, -1);
    }

    public void abort(int timeout)
    {
        abort(AMQP.REPLY_SUCCESS, "OK", timeout);
    }

    public void abort(int closeCode, String closeMessage, int timeout)
    {
        try {
            close(closeCode, closeMessage, true, null, timeout, true);
        } catch (IOException e) {
            Utility.emptyStatement();
        }
    }
    
    public void close(int closeCode,
                      String closeMessage,
                      boolean initiatedByApplication,
                      Throwable cause)
        throws IOException
    {
        close(closeCode, closeMessage, initiatedByApplication, cause, 0, false);
    }

    /**
     * Protected API - Close this connection with the given code, message, source
     * and timeout value for all the close operations to complete.
     * Specifies if any encountered exceptions should be ignored.
     */
    public void close(int closeCode,
                      String closeMessage,
                      boolean initiatedByApplication,
                      Throwable cause,
                      int timeout,
                      boolean abort)
        throws IOException
    {
        try {
            AMQImpl.Connection.Close reason =
                new AMQImpl.Connection.Close(closeCode, closeMessage, 0, 0);
            shutdown(reason, initiatedByApplication, cause, true);
            AMQChannel.SimpleBlockingRpcContinuation k =
                new AMQChannel.SimpleBlockingRpcContinuation();
            _channel0.quiescingRpc(reason, k);
            k.getReply(timeout);
        } catch (TimeoutException tte) {
            if (!abort)
                throw new ShutdownSignalException(true, true, tte, this);
        } catch (ShutdownSignalException sse) {
            if (!abort)
                throw sse;
        } catch (IOException ioe) {
            if (!abort)
                throw ioe;
        } finally {
            _frameHandler.close();
        }
    }

    @Override public String toString() {
        return "amqp://" + _params.getUserName() + "@" + getHost() + ":" + getPort() + _params.getVirtualHost();
    }
}
