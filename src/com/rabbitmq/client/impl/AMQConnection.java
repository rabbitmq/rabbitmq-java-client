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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
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
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MissedHeartbeatException;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.BlockingCell;
import com.rabbitmq.utility.Utility;

/**
 * Concrete class representing and managing an AMQP connection to a broker.
 * <p>
 * To create a broker connection, use {@link ConnectionFactory}.  See {@link Connection}
 * for an example.
 */
public class AMQConnection extends ShutdownNotifierComponent implements Connection {
    /** Timeout used while waiting for AMQP handshaking to complete (milliseconds) */
    public static final int HANDSHAKE_TIMEOUT = 10000;

    /** Timeout used while waiting for a connection.close-ok (milliseconds) */
    public static final int CONNECTION_CLOSING_TIMEOUT = 10000;

    /**
     * Retrieve a copy of the default table of client properties that
     * will be sent to the server during connection startup. This
     * method is called when each new ConnectionFactory instance is
     * constructed.
     * @return a map of client properties
     * @see Connection#getClientProperties
     */
    public static Map<String, Object> defaultClientProperties() {
        return Frame.buildTable(new Object[] {
                "product", LongStringHelper.asLongString("RabbitMQ"),
                "version", LongStringHelper.asLongString(ClientVersion.VERSION),
                "platform", LongStringHelper.asLongString("Java"),
                "copyright", LongStringHelper.asLongString(
                    "Copyright (C) 2007-2008 LShift Ltd., " +
                    "Cohesive Financial Technologies LLC., " +
                    "and Rabbit Technologies Ltd."),
                "information", LongStringHelper.asLongString(
                    "Licensed under the MPL. See http://www.rabbitmq.com/")
            });
    }

    private static final Version clientVersion =
        new Version(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR);

    /** Initialization parameters */
    private final ConnectionFactory factory;

    /** The special channel 0 */
    private final AMQChannel _channel0 = new AMQChannel(this, 0) {
            @Override public boolean processAsync(Command c) throws IOException {
                return _connection.processControlCommand(c);
            }
        };

    /** Object that manages a set of channels */
    public ChannelManager _channelManager = new ChannelManager(0);

    /** Frame source/sink */
    private final FrameHandler _frameHandler;

    /** Flag controlling the main driver loop's termination */
    private volatile boolean _running = false;

    /** Maximum frame length, or zero if no limit is set */
    private int _frameMax;

    /** Handler for (otherwise-unhandled) exceptions that crop up in the mainloop. */
    private final ExceptionHandler _exceptionHandler;

    /**
     * Object used for blocking main application thread when doing all the necessary
     * connection shutdown operations
     */
    private BlockingCell<Object> _appContinuation = new BlockingCell<Object>();

    /** Flag indicating whether the client received Connection.Close message from the broker */
    private boolean _brokerInitiatedShutdown = false;

    /** Manages heartbeats for this connection */
    private final Heartbeater heartbeater;

    /**
     * Protected API - respond, in the driver thread, to a ShutdownSignal.
     * @param channel the channel to disconnect
     */
    public final void disconnectChannel(ChannelN channel) {
        _channelManager.disconnectChannel(channel);
    }

    public void ensureIsOpen()
        throws AlreadyClosedException
    {
        if (!isOpen()) {
            throw new AlreadyClosedException("Attempt to use closed connection", this);
        }
    }

    /**
     * Count of socket-timeouts that have happened without any incoming frames
     */
    private int _missedHeartbeats;

    /**
     * Currently-configured heartbeat interval, in seconds. 0 meaning none.
     */
    private int _heartbeat;

    private final String _username, _password, _virtualHost;
    private final int _requestedChannelMax, _requestedFrameMax, _requestedHeartbeat;
    private final Map<String, Object> _clientProperties;

    /** Saved server properties field from connection.start */
    public Map<String, Object> _serverProperties;

    /** {@inheritDoc} */
    public String getHost() {
        return _frameHandler.getHost();
    }

    /** {@inheritDoc} */
    public int getPort() {
        return _frameHandler.getPort();
    }

    public FrameHandler getFrameHandler(){
        return _frameHandler;
    }

    /** {@inheritDoc} */
    public Map<String, Object> getServerProperties() {
        return _serverProperties;
    }

    /**
     * Construct a new connection to a broker.
     * @param factory the initialization parameters for a connection
     * @param frameHandler interface to an object that will handle the frame I/O for this connection
     */
    public AMQConnection(ConnectionFactory factory,
                         FrameHandler frameHandler) {
        this(factory, frameHandler, new DefaultExceptionHandler());
    }

    /**
     * Construct a new connection to a broker.
     * @param factory the initialization parameters for a connection
     * @param frameHandler interface to an object that will handle the frame I/O for this connection
     * @param exceptionHandler interface to an object that will handle any special exceptions encountered while using this connection
     */
    public AMQConnection(ConnectionFactory factory,
                         FrameHandler frameHandler,
                         ExceptionHandler exceptionHandler)
    {
        checkPreconditions();

        _username = factory.getUsername();
        _password = factory.getPassword();
        _virtualHost = factory.getVirtualHost();
        _requestedChannelMax = factory.getRequestedChannelMax();
        _requestedFrameMax = factory.getRequestedFrameMax();
        _requestedHeartbeat = factory.getRequestedHeartbeat();
        _clientProperties = new HashMap<String, Object>(factory.getClientProperties());

        this.factory = factory;
        this.heartbeater = new Heartbeater(frameHandler);
        _frameHandler = frameHandler;
        _running = true;
        _frameMax = 0;
        _missedHeartbeats = 0;
        _heartbeat = 0;
        _exceptionHandler = exceptionHandler;
        _brokerInitiatedShutdown = false;
    }

    /**
     * Start up the connection, including the MainLoop thread.
     * Sends the protocol
     * version negotiation header, and runs through
     * Connection.Start/.StartOk, Connection.Tune/.TuneOk, and then
     * calls Connection.Open and waits for the OpenOk. Sets heartbeat
     * and frame max values after tuning has taken place.
     * @throws java.io.IOException if an error is encountered
     */
    public void start()
        throws IOException
    {
        // Make sure that the first thing we do is to send the header,
        // which should cause any socket errors to show up for us, rather
        // than risking them pop out in the MainLoop
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

        // start the main loop going
        Thread ml = new MainLoop();
        ml.setName("AMQP Connection " + getHost() + ":" + getPort());
        ml.start();

        try {
            AMQP.Connection.Start connStart =
                (AMQP.Connection.Start) connStartBlocker.getReply().getMethod();

            _serverProperties = connStart.getServerProperties();
        
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
        
        LongString saslResponse = LongStringHelper.asLongString("\0" + _username +
                                                                "\0" + _password);
        AMQImpl.Connection.StartOk startOk =
            new AMQImpl.Connection.StartOk(_clientProperties, "PLAIN",
                                           saslResponse, "en_US");
        
        AMQP.Connection.Tune connTune = null;

        try {
            connTune = (AMQP.Connection.Tune) _channel0.rpc(startOk).getMethod();
        } catch (ShutdownSignalException e) {
            throw AMQChannel.wrap(e, "Possibly caused by authentication failure");
        }

        int channelMax =
            negotiatedMaxValue(factory.getRequestedChannelMax(),
                               connTune.getChannelMax());
        _channelManager = new ChannelManager(channelMax);
        
        int frameMax =
            negotiatedMaxValue(factory.getRequestedFrameMax(),
                               connTune.getFrameMax());
        setFrameMax(frameMax);

        int heartbeat =
            negotiatedMaxValue(factory.getRequestedHeartbeat(),
                               connTune.getHeartbeat());
        setHeartbeat(heartbeat);
        
        _channel0.transmit(new AMQImpl.Connection.TuneOk(channelMax,
                                                         frameMax,
                                                         heartbeat));
        // 0.9.1: insist [on not being redirected] is deprecated, but
        // still in generated code; just pass a dummy value here
        Method res = _channel0.exnWrappingRpc(new AMQImpl.Connection.Open(_virtualHost,
                                                                          "",
                                                                          false)).getMethod();
        return;
    }

    /**
     * Private API - check required preconditions and protocol invariants
     */
    public void checkPreconditions() {
        AMQCommand.checkEmptyContentBodyFrameSize();
    }

    /** {@inheritDoc} */
    public int getChannelMax() {
        return _channelManager.getChannelMax();
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    public int getHeartbeat() {
        return _heartbeat;
    }

    /**
     * Protected API - set the heartbeat timeout. Should only be called
     * during tuning.
     */
    public void setHeartbeat(int heartbeat) {
        try {
            this.heartbeater.setHeartbeat(heartbeat);

            // Divide by four to make the maximum unwanted delay in
            // sending a timeout be less than a quarter of the
            // timeout setting.
            _frameHandler.setTimeout(heartbeat * 1000 / 4);
        } catch (SocketException se) {
            // should do more here?
        }
    }

    public Map<String, Object> getClientProperties() {
        return new HashMap<String, Object>(_clientProperties);
    }

    /**
     * Protected API - retrieve the current ExceptionHandler
     */
    public ExceptionHandler getExceptionHandler() {
        return _exceptionHandler;
    }

    /** Public API - {@inheritDoc} */
    public Channel createChannel(int channelNumber) throws IOException {
        ensureIsOpen();
        return _channelManager.createChannel(this, channelNumber);
    }

    /** Public API - {@inheritDoc} */
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
        this.heartbeater.signalActivity();
    }

    private static int negotiatedMaxValue(int clientValue, int serverValue) {
        return (clientValue == 0 || serverValue == 0) ?
            Math.max(clientValue, serverValue) :
            Math.min(clientValue, serverValue);
    }

    private class MainLoop extends Thread {

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
                                    _channelManager
                                        .getChannel(frame.channel)
                                        .handleFrame(frame);
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
            if (isOpen()) {
                handleConnectionClose(c);
            } else {
                // Already shutting down, so just send back a CloseOk.
                try {
                    _channel0.quiescingTransmit(new AMQImpl.Connection.CloseOk());
                } catch (IOException ioe) {
                    Utility.emptyStatement();
                }
            }
            return true;
        } else {
            if (isOpen()) {
                // Normal command.
                return false;
            } else {
                // Quiescing.
                if (method instanceof AMQP.Connection.CloseOk) {    
                    // It's our final "RPC". Time to shut down.
                    _running = false;
                    // If Close was sent from within the MainLoop we
                    // will not have a continuation to return to, so
                    // we treat this as processed in that case.
                    return _channel0._activeRpc == null;
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
        Thread scw = new SocketCloseWait(sse);
        scw.setName("AMQP Connection Closing Monitor " +
                    getHost() + ":" + getPort());
        scw.start();
    }
    
    private class SocketCloseWait extends Thread {
        private ShutdownSignalException cause;
        
        public SocketCloseWait(ShutdownSignalException sse) {
            cause = sse;
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

        // stop any heartbeating
        this.heartbeater.shutdown();

        _channel0.processShutdownSignal(sse, !initiatedByApplication, notifyRpc);
        _channelManager.handleSignal(sse);
        return sse;
    }

    /** Public API - {@inheritDoc} */
    public void close()
        throws IOException
    {
        close(-1);
    }

    /** Public API - {@inheritDoc} */
    public void close(int timeout)
        throws IOException
    {
        close(AMQP.REPLY_SUCCESS, "OK", timeout);
    }

    /** Public API - {@inheritDoc} */
    public void close(int closeCode, String closeMessage)
        throws IOException
    {
        close(closeCode, closeMessage, -1);
    }

    /** Public API - {@inheritDoc} */
    public void close(int closeCode, String closeMessage, int timeout)
        throws IOException
    {
        close(closeCode, closeMessage, true, null, timeout, false);
    }

    /** Public API - {@inheritDoc} */
    public void abort()
    {
        abort(-1);
    }

    /** Public API - {@inheritDoc} */
    public void abort(int closeCode, String closeMessage)
    {
       abort(closeCode, closeMessage, -1);
    }

    /** Public API - {@inheritDoc} */
    public void abort(int timeout)
    {
        abort(AMQP.REPLY_SUCCESS, "OK", timeout);
    }

    /** Public API - {@inheritDoc} */
    public void abort(int closeCode, String closeMessage, int timeout)
    {
        try {
            close(closeCode, closeMessage, true, null, timeout, true);
        } catch (IOException e) {
            Utility.emptyStatement();
        }
    }

    /** 
     * Protected API - Delegates to {@link
     * #close(int,String,boolean,Throwable,int,boolean) the
     * six-argument close method}, passing 0 for the timeout, and
     * false for the abort flag.
     */
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
        final boolean sync = !(Thread.currentThread() instanceof MainLoop);

        try {
            AMQImpl.Connection.Close reason =
                new AMQImpl.Connection.Close(closeCode, closeMessage, 0, 0);

            shutdown(reason, initiatedByApplication, cause, true);
            if(sync){
              AMQChannel.SimpleBlockingRpcContinuation k =
                  new AMQChannel.SimpleBlockingRpcContinuation();
              _channel0.quiescingRpc(reason, k);
              k.getReply(timeout);
            } else {
              _channel0.quiescingTransmit(reason);
            }
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
            if(sync) _frameHandler.close();
        }
    }

    @Override public String toString() {
        return "amqp://" + _username + "@" + getHost() + ":" + getPort() + _virtualHost;
    }
}
