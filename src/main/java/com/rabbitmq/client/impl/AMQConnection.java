// Copyright (c) 2007-2019 Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.*;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.impl.AMQChannel.BlockingRpcContinuation;
import com.rabbitmq.client.impl.recovery.RecoveryCanBeginListener;
import com.rabbitmq.utility.BlockingCell;
import com.rabbitmq.utility.Utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class Copyright {
    final static String COPYRIGHT="Copyright (c) 2007-2019 Pivotal Software, Inc.";
    final static String LICENSE="Licensed under the MPL. See https://www.rabbitmq.com/";
}

/**
 * Concrete class representing and managing an AMQP connection to a broker.
 * <p>
 * To create a broker connection, use {@link ConnectionFactory}.  See {@link Connection}
 * for an example.
 */
public class AMQConnection extends ShutdownNotifierComponent implements Connection, NetworkConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(AMQConnection.class);
    // we want socket write and channel shutdown timeouts to kick in after
    // the heartbeat one, so we use a value of 105% of the effective heartbeat timeout
    static final double CHANNEL_SHUTDOWN_TIMEOUT_MULTIPLIER = 1.05;

    private final ExecutorService consumerWorkServiceExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService shutdownExecutor;
    private Thread mainLoopThread;
    private ThreadFactory threadFactory = Executors.defaultThreadFactory();
    private String id;

    private final List<RecoveryCanBeginListener> recoveryCanBeginListeners =
            Collections.synchronizedList(new ArrayList<>());

    private final ErrorOnWriteListener errorOnWriteListener;

    private final int workPoolTimeout;

    private final AtomicBoolean finalShutdownStarted = new AtomicBoolean(false);

    /**
     * Retrieve a copy of the default table of client properties that
     * will be sent to the server during connection startup. This
     * method is called when each new ConnectionFactory instance is
     * constructed.
     * @return a map of client properties
     * @see Connection#getClientProperties
     */
    public static Map<String, Object> defaultClientProperties() {
        Map<String,Object> props = new HashMap<>();
        props.put("product", LongStringHelper.asLongString("RabbitMQ"));
        props.put("version", LongStringHelper.asLongString(ClientVersion.VERSION));
        props.put("platform", LongStringHelper.asLongString("Java"));
        props.put("copyright", LongStringHelper.asLongString(Copyright.COPYRIGHT));
        props.put("information", LongStringHelper.asLongString(Copyright.LICENSE));

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("publisher_confirms", true);
        capabilities.put("exchange_exchange_bindings", true);
        capabilities.put("basic.nack", true);
        capabilities.put("consumer_cancel_notify", true);
        capabilities.put("connection.blocked", true);
        capabilities.put("authentication_failure_close", true);

        props.put("capabilities", capabilities);

        return props;
    }

    private static final Version clientVersion =
        new Version(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR);

    /** The special channel 0 (<i>not</i> managed by the <code><b>_channelManager</b></code>) */
    private final AMQChannel _channel0;

    protected ConsumerWorkService _workService = null;

    /** Frame source/sink */
    private final FrameHandler _frameHandler;

    /** Flag controlling the main driver loop's termination */
    private volatile boolean _running = false;

    /** Handler for (uncaught) exceptions that crop up in the {@link MainLoop}. */
    private final ExceptionHandler _exceptionHandler;

    /** Object used for blocking main application thread when doing all the necessary
     * connection shutdown operations
     */
    private final BlockingCell<Object> _appContinuation = new BlockingCell<>();

    /** Flag indicating whether the client received Connection.Close message from the broker */
    private volatile boolean _brokerInitiatedShutdown;

    /** Flag indicating we are still negotiating the connection in start */
    private volatile boolean _inConnectionNegotiation;

    /** Manages heart-beat sending for this connection */
    private HeartbeatSender _heartbeatSender;

    private final String _virtualHost;
    private final Map<String, Object> _clientProperties;
    private final SaslConfig saslConfig;
    private final int requestedHeartbeat;
    private final int requestedChannelMax;
    private final int requestedFrameMax;
    private final int handshakeTimeout;
    private final int shutdownTimeout;
    private final CredentialsProvider credentialsProvider;
    private final Collection<BlockedListener> blockedListeners = new CopyOnWriteArrayList<>();
    protected final MetricsCollector metricsCollector;
    private final int channelRpcTimeout;
    private final boolean channelShouldCheckRpcResponseType;
    private final TrafficListener trafficListener;
    private final CredentialsRefreshService credentialsRefreshService;

    /* State modified after start - all volatile */

    /** Maximum frame length, or zero if no limit is set */
    private volatile int _frameMax = 0;
    /** Count of socket-timeouts that have happened without any incoming frames */
    private volatile int _missedHeartbeats = 0;
    /** Currently-configured heart-beat interval, in seconds. 0 meaning none. */
    private volatile int _heartbeat = 0;
    /** Object that manages a set of channels */
    private volatile ChannelManager _channelManager;
    /** Saved server properties field from connection.start */
    private volatile Map<String, Object> _serverProperties;

    /**
     * Protected API - respond, in the main I/O loop thread, to a ShutdownSignal.
     * @param channel the channel to disconnect
     */
    final void disconnectChannel(ChannelN channel) {
        ChannelManager cm = _channelManager;
        if (cm != null)
            cm.releaseChannelNumber(channel);
    }

    private void ensureIsOpen()
        throws AlreadyClosedException
    {
        if (!isOpen()) {
            throw new AlreadyClosedException(getCloseReason());
        }
    }

    /** {@inheritDoc} */
    @Override
    public InetAddress getAddress() {
        return _frameHandler.getAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return _frameHandler.getLocalAddress();
    }

    /** {@inheritDoc} */
    @Override
    public int getPort() {
        return _frameHandler.getPort();
    }

    @Override
    public int getLocalPort() {
        return _frameHandler.getLocalPort();
    }

    public FrameHandler getFrameHandler(){
        return _frameHandler;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getServerProperties() {
        return _serverProperties;
    }

    public AMQConnection(ConnectionParams params, FrameHandler frameHandler) {
        this(params, frameHandler, new NoOpMetricsCollector());
    }

    /** Construct a new connection
     * @param params parameters for it
     */
    public AMQConnection(ConnectionParams params, FrameHandler frameHandler, MetricsCollector metricsCollector)
    {
        checkPreconditions();
        this.credentialsProvider = params.getCredentialsProvider();
        this._frameHandler = frameHandler;
        this._virtualHost = params.getVirtualHost();
        this._exceptionHandler = params.getExceptionHandler();

        this._clientProperties = new HashMap<>(params.getClientProperties());
        this.requestedFrameMax = params.getRequestedFrameMax();
        this.requestedChannelMax = params.getRequestedChannelMax();
        this.requestedHeartbeat = params.getRequestedHeartbeat();
        this.handshakeTimeout = params.getHandshakeTimeout();
        this.shutdownTimeout = params.getShutdownTimeout();
        this.saslConfig = params.getSaslConfig();
        this.consumerWorkServiceExecutor = params.getConsumerWorkServiceExecutor();
        this.heartbeatExecutor = params.getHeartbeatExecutor();
        this.shutdownExecutor = params.getShutdownExecutor();
        this.threadFactory = params.getThreadFactory();
        if(params.getChannelRpcTimeout() < 0) {
            throw new IllegalArgumentException("Continuation timeout on RPC calls cannot be less than 0");
        }
        this.channelRpcTimeout = params.getChannelRpcTimeout();
        this.channelShouldCheckRpcResponseType = params.channelShouldCheckRpcResponseType();

        this.trafficListener = params.getTrafficListener() == null ? TrafficListener.NO_OP : params.getTrafficListener();

        this.credentialsRefreshService = params.getCredentialsRefreshService();

        this._channel0 = createChannel0();

        this._channelManager = null;

        this._brokerInitiatedShutdown = false;

        this._inConnectionNegotiation = true; // we start out waiting for the first protocol response

        this.metricsCollector = metricsCollector;

        this.errorOnWriteListener = params.getErrorOnWriteListener() != null ? params.getErrorOnWriteListener() :
            (connection, exception) -> { throw exception; }; // we just propagate the exception for non-recoverable connections
        this.workPoolTimeout = params.getWorkPoolTimeout();
    }

    AMQChannel createChannel0() {
        return new AMQChannel(this, 0) {
            @Override public boolean processAsync(Command c) throws IOException {
                return getConnection().processControlCommand(c);
            }
        };
    }

    private void initializeConsumerWorkService() {
        this._workService  = new ConsumerWorkService(consumerWorkServiceExecutor, threadFactory, workPoolTimeout, shutdownTimeout);
    }

    private void initializeHeartbeatSender() {
        this._heartbeatSender = new HeartbeatSender(_frameHandler, heartbeatExecutor, threadFactory);
    }

    /**
     * Start up the connection, including the MainLoop thread.
     * Sends the protocol
     * version negotiation header, and runs through
     * Connection.Start/.StartOk, Connection.Tune/.TuneOk, and then
     * calls Connection.Open and waits for the OpenOk. Sets heart-beat
     * and frame max values after tuning has taken place.
     * @throws IOException if an error is encountered
     * either before, or during, protocol negotiation;
     * sub-classes {@link ProtocolVersionMismatchException} and
     * {@link PossibleAuthenticationFailureException} will be thrown in the
     * corresponding circumstances. {@link AuthenticationFailureException}
     * will be thrown if the broker closes the connection with ACCESS_REFUSED.
     * If an exception is thrown, connection resources allocated can all be
     * garbage collected when the connection object is no longer referenced.
     */
    public void start()
            throws IOException, TimeoutException {
        initializeConsumerWorkService();
        initializeHeartbeatSender();
        this._running = true;
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
        try {
            // The following two lines are akin to AMQChannel's
            // transmit() method for this pseudo-RPC.
            _frameHandler.setTimeout(handshakeTimeout);
            _frameHandler.sendHeader();
        } catch (IOException ioe) {
            _frameHandler.close();
            throw ioe;
        }

        this._frameHandler.initialize(this);

        AMQP.Connection.Start connStart;
        AMQP.Connection.Tune connTune = null;
        try {
            connStart =
                    (AMQP.Connection.Start) connStartBlocker.getReply(handshakeTimeout/2).getMethod();

            _serverProperties = Collections.unmodifiableMap(connStart.getServerProperties());

            Version serverVersion =
                    new Version(connStart.getVersionMajor(),
                                       connStart.getVersionMinor());

            if (!Version.checkVersion(clientVersion, serverVersion)) {
                throw new ProtocolVersionMismatchException(clientVersion,
                                                                  serverVersion);
            }

            String[] mechanisms = connStart.getMechanisms().toString().split(" ");
            SaslMechanism sm = this.saslConfig.getSaslMechanism(mechanisms);
            if (sm == null) {
                throw new IOException("No compatible authentication mechanism found - " +
                                              "server offered [" + connStart.getMechanisms() + "]");
            }

            String username = credentialsProvider.getUsername();
            String password = credentialsProvider.getPassword();

            if (credentialsProvider.getTimeBeforeExpiration() != null) {
                if (this.credentialsRefreshService == null) {
                    throw new IllegalStateException("Credentials can expire, a credentials refresh service should be set");
                }
                if (this.credentialsRefreshService.isApproachingExpiration(credentialsProvider.getTimeBeforeExpiration())) {
                    credentialsProvider.refresh();
                    username = credentialsProvider.getUsername();
                    password = credentialsProvider.getPassword();
                }
            }

            LongString challenge = null;
            LongString response = sm.handleChallenge(null, username, password);

            do {
                Method method = (challenge == null)
                                        ? new AMQP.Connection.StartOk.Builder()
                                                  .clientProperties(_clientProperties)
                                                  .mechanism(sm.getName())
                                                  .response(response)
                                                  .build()
                                        : new AMQP.Connection.SecureOk.Builder().response(response).build();

                try {
                    Method serverResponse = _channel0.rpc(method, handshakeTimeout/2).getMethod();
                    if (serverResponse instanceof AMQP.Connection.Tune) {
                        connTune = (AMQP.Connection.Tune) serverResponse;
                    } else {
                        challenge = ((AMQP.Connection.Secure) serverResponse).getChallenge();
                        response = sm.handleChallenge(challenge, username, password);
                    }
                } catch (ShutdownSignalException e) {
                    Method shutdownMethod = e.getReason();
                    if (shutdownMethod instanceof AMQP.Connection.Close) {
                        AMQP.Connection.Close shutdownClose = (AMQP.Connection.Close) shutdownMethod;
                        if (shutdownClose.getReplyCode() == AMQP.ACCESS_REFUSED) {
                            throw new AuthenticationFailureException(shutdownClose.getReplyText());
                        }
                    }
                    throw new PossibleAuthenticationFailureException(e);
                }
            } while (connTune == null);
        } catch (TimeoutException | IOException te) {
            _frameHandler.close();
            throw te;
        } catch (ShutdownSignalException sse) {
            _frameHandler.close();
            throw AMQChannel.wrap(sse);
        }

        try {
            int channelMax =
                negotiateChannelMax(this.requestedChannelMax,
                                    connTune.getChannelMax());
            _channelManager = instantiateChannelManager(channelMax, threadFactory);

            int frameMax =
                negotiatedMaxValue(this.requestedFrameMax,
                                   connTune.getFrameMax());
            this._frameMax = frameMax;

            int heartbeat =
                negotiatedMaxValue(this.requestedHeartbeat,
                                   connTune.getHeartbeat());

            setHeartbeat(heartbeat);

            _channel0.transmit(new AMQP.Connection.TuneOk.Builder()
                                .channelMax(channelMax)
                                .frameMax(frameMax)
                                .heartbeat(heartbeat)
                              .build());
            _channel0.exnWrappingRpc(new AMQP.Connection.Open.Builder()
                                      .virtualHost(_virtualHost)
                                    .build());
        } catch (IOException ioe) {
            _heartbeatSender.shutdown();
            _frameHandler.close();
            throw ioe;
        } catch (ShutdownSignalException sse) {
            _heartbeatSender.shutdown();
            _frameHandler.close();
            throw AMQChannel.wrap(sse);
        }

        if (this.credentialsProvider.getTimeBeforeExpiration() != null) {
            String registrationId = this.credentialsRefreshService.register(credentialsProvider, () -> {
                // return false if connection is closed, so refresh service can get rid of this registration
                if (!isOpen()) {
                    return false;
                }
                if (this._inConnectionNegotiation) {
                    // this should not happen
                    return true;
                }
                String refreshedPassword = credentialsProvider.getPassword();

                AMQImpl.Connection.UpdateSecret updateSecret = new AMQImpl.Connection.UpdateSecret(
                        LongStringHelper.asLongString(refreshedPassword), "Refresh scheduled by client"
                );
                try {
                    _channel0.rpc(updateSecret);
                } catch (ShutdownSignalException e) {
                    LOGGER.warn("Error while trying to update secret: {}. Connection has been closed.", e.getMessage());
                    return false;
                }
                return true;
            });

            addShutdownListener(sse -> this.credentialsRefreshService.unregister(this.credentialsProvider, registrationId));
        }

        // We can now respond to errors having finished tailoring the connection
        this._inConnectionNegotiation = false;
    }

    protected ChannelManager instantiateChannelManager(int channelMax, ThreadFactory threadFactory) {
        ChannelManager result = new ChannelManager(this._workService, channelMax, threadFactory, this.metricsCollector);
        configureChannelManager(result);
        return result;
    }

    protected void configureChannelManager(ChannelManager channelManager) {
        channelManager.setShutdownExecutor(this.shutdownExecutor);
        channelManager.setChannelShutdownTimeout((int) ((requestedHeartbeat * CHANNEL_SHUTDOWN_TIMEOUT_MULTIPLIER) * 1000));
    }

    /**
     * Package private API, allows for easier testing.
     */
    public void startMainLoop() {
        MainLoop loop = new MainLoop();
        final String name = "AMQP Connection " + getHostAddress() + ":" + getPort();
        mainLoopThread = Environment.newThread(threadFactory, loop, name);
        mainLoopThread.start();
    }

    /**
     * Private API, allows for easier simulation of bogus clients.
     */
    protected int negotiateChannelMax(int requestedChannelMax, int serverMax) {
        return negotiatedMaxValue(requestedChannelMax, serverMax);
    }

    /**
     * Private API - check required preconditions and protocol invariants
     */
    private static void checkPreconditions() {
        AMQCommand.checkPreconditions();
    }

    /** {@inheritDoc} */
    @Override
    public int getChannelMax() {
        ChannelManager cm = _channelManager;
        if (cm == null) return 0;
        return cm.getChannelMax();
    }

    /** {@inheritDoc} */
    @Override
    public int getFrameMax() {
        return _frameMax;
    }

    /** {@inheritDoc} */
    @Override
    public int getHeartbeat() {
        return _heartbeat;
    }

    /**
     * Protected API - set the heartbeat timeout. Should only be called
     * during tuning.
     */
    public void setHeartbeat(int heartbeat) {
        try {
            _heartbeatSender.setHeartbeat(heartbeat);
            _heartbeat = heartbeat;

            // Divide by four to make the maximum unwanted delay in
            // sending a timeout be less than a quarter of the
            // timeout setting.
            _frameHandler.setTimeout(heartbeat * 1000 / 4);
        } catch (SocketException se) {
            // should do more here?
        }
    }

    /**
     * Makes it possible to override thread factory that is used
     * to instantiate connection network I/O loop. Only necessary
     * in the environments with restricted
     * @param threadFactory thread factory to use
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
     * @return Thread factory used by this connection.
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    @Override
    public Map<String, Object> getClientProperties() {
        return new HashMap<>(_clientProperties);
    }

    @Override
    public String getClientProvidedName() {
        return (String) _clientProperties.get("connection_name");
    }

    /**
     * Protected API - retrieve the current ExceptionHandler
     */
    @Override
    public ExceptionHandler getExceptionHandler() {
        return _exceptionHandler;
    }


    /** Public API
     *
     * @return true if this work service instance uses its own consumerWorkServiceExecutor (as opposed to a shared one)
     */
    public boolean willShutDownConsumerExecutor() {
        return this._workService.usesPrivateExecutor();
    }


    /** Public API - {@inheritDoc} */
    @Override
    public Channel createChannel(int channelNumber) throws IOException {
        ensureIsOpen();
        ChannelManager cm = _channelManager;
        if (cm == null) return null;
        Channel channel = cm.createChannel(this, channelNumber);
        metricsCollector.newChannel(channel);
        return channel;
    }

    /** Public API - {@inheritDoc} */
    @Override
    public Channel createChannel() throws IOException {
        ensureIsOpen();
        ChannelManager cm = _channelManager;
        if (cm == null) return null;
        Channel channel = cm.createChannel(this);
        metricsCollector.newChannel(channel);
        return channel;
    }

    /**
     * Public API - sends a frame directly to the broker.
     */
    void writeFrame(Frame f) throws IOException {
        _frameHandler.writeFrame(f);
        _heartbeatSender.signalActivity();
    }

    /**
     * Public API - flush the output buffers
     */
    public void flush() throws IOException {
        try {
            _frameHandler.flush();
        } catch (IOException ioe) {
            this.errorOnWriteListener.handle(this, ioe);
        }
    }

    private static int negotiatedMaxValue(int clientValue, int serverValue) {
        return (clientValue == 0 || serverValue == 0) ?
            Math.max(clientValue, serverValue) :
            Math.min(clientValue, serverValue);
    }

    private class MainLoop implements Runnable {

        /**
         * Channel reader thread main loop. Reads a frame, and if it is
         * not a heartbeat frame, dispatches it to the channel it refers to.
         * Continues running until the "running" flag is set false by
         * shutdown().
         */
        @Override
        public void run() {
            boolean shouldDoFinalShutdown = true;
            try {
                while (_running) {
                    Frame frame = _frameHandler.readFrame();
                    readFrame(frame);
                }
            } catch (Throwable ex) {
                if (ex instanceof InterruptedException) {
                    // loop has been interrupted during shutdown,
                    // no need to do it again
                    shouldDoFinalShutdown = false;
                } else {
                    handleFailure(ex);
                }
            } finally {
                if (shouldDoFinalShutdown) {
                    doFinalShutdown();
                }
            }
        }
    }

    /** private API */
    public boolean handleReadFrame(Frame frame) {
        if(_running) {
            try {
                readFrame(frame);
                return true;
            } catch (WorkPoolFullException e) {
                // work pool is full, we propagate this one.
                throw e;
            } catch (Throwable ex) {
                try {
                    handleFailure(ex);
                } finally {
                    doFinalShutdown();
                }
            }
        }
        return false;
    }

    public boolean isRunning() {
        return _running;
    }

    public boolean hasBrokerInitiatedShutdown() {
        return _brokerInitiatedShutdown;
    }

    private void readFrame(Frame frame) throws IOException {
        if (frame != null) {
            _missedHeartbeats = 0;
            if (frame.getType() == AMQP.FRAME_HEARTBEAT) {
                // Ignore it: we've already just reset the heartbeat counter.
            } else {
                if (frame.getChannel() == 0) { // the special channel
                    _channel0.handleFrame(frame);
                } else {
                    if (isOpen()) {
                        // If we're still _running, but not isOpen(), then we
                        // must be quiescing, which means any inbound frames
                        // for non-zero channels (and any inbound commands on
                        // channel zero that aren't Connection.CloseOk) must
                        // be discarded.
                        ChannelManager cm = _channelManager;
                        if (cm != null) {
                            ChannelN channel;
                            try {
                                channel = cm.getChannel(frame.getChannel());
                            } catch(UnknownChannelException e) {
                                // this can happen if channel has been closed,
                                // but there was e.g. an in-flight delivery.
                                // just ignoring the frame to avoid closing the whole connection
                                LOGGER.info("Received a frame on an unknown channel, ignoring it");
                                return;
                            }
                            channel.handleFrame(frame);
                        }
                    }
                }
            }
        } else {
            // Socket timeout waiting for a frame.
            // Maybe missed heartbeat.
            handleSocketTimeout();
        }
    }

    /** private API */
    public void handleHeartbeatFailure() {
        Exception ex = new MissedHeartbeatException("Heartbeat missing with heartbeat = " +
            _heartbeat + " seconds");
        try {
            _exceptionHandler.handleUnexpectedConnectionDriverException(this, ex);
            shutdown(null, false, ex, true);
        } finally {
            doFinalShutdown();
        }
    }

    /** private API */
    public void handleIoError(Throwable ex) {
        try {
            handleFailure(ex);
        } finally {
            doFinalShutdown();
        }
    }

    private void handleFailure(Throwable ex)  {
        if(ex instanceof EOFException) {
            if (!_brokerInitiatedShutdown)
                shutdown(null, false, ex, true);
        } else {
            _exceptionHandler.handleUnexpectedConnectionDriverException(AMQConnection.this,
                ex);
            shutdown(null, false, ex, true);
        }
    }

    /** private API */
    public void doFinalShutdown() {
        if (finalShutdownStarted.compareAndSet(false, true)) {
            _frameHandler.close();
            _appContinuation.set(null);
            closeMainLoopThreadIfNecessary();
            notifyListeners();
            // assuming that shutdown listeners do not do anything
            // asynchronously, e.g. start new threads, this effectively
            // guarantees that we only begin recovery when all shutdown
            // listeners have executed
            notifyRecoveryCanBeginListeners();
        }
    }

    private void closeMainLoopThreadIfNecessary() {
        if (mainLoopReadThreadNotNull() && notInMainLoopThread()) {
            if (this.mainLoopThread.isAlive()) {
                this.mainLoopThread.interrupt();
            }
        }
    }

    private boolean notInMainLoopThread() {
        return Thread.currentThread() != this.mainLoopThread;
    }

    private boolean mainLoopReadThreadNotNull() {
        return this.mainLoopThread != null;
    }

    private void notifyRecoveryCanBeginListeners() {
        ShutdownSignalException sse = this.getCloseReason();
        for(RecoveryCanBeginListener fn : Utility.copy(this.recoveryCanBeginListeners)) {
            fn.recoveryCanBegin(sse);
        }
    }

    public void addRecoveryCanBeginListener(RecoveryCanBeginListener fn) {
        this.recoveryCanBeginListeners.add(fn);
    }

    @SuppressWarnings("unused")
    public void removeRecoveryCanBeginListener(RecoveryCanBeginListener fn) {
        this.recoveryCanBeginListeners.remove(fn);
    }

    /**
     * Called when a frame-read operation times out
     * @throws MissedHeartbeatException if heart-beats have been missed
     */
    private void handleSocketTimeout() throws SocketTimeoutException {
        if (_inConnectionNegotiation) {
            throw new SocketTimeoutException("Timeout during Connection negotiation");
        }

        if (_heartbeat == 0) { // No heart-beating
            return;
        }

        // We check against 8 = 2 * 4 because we need to wait for at
        // least two complete heartbeat setting intervals before
        // complaining, and we've set the socket timeout to a quarter
        // of the heartbeat setting in setHeartbeat above.
        if (++_missedHeartbeats > (2 * 4)) {
            throw new MissedHeartbeatException("Heartbeat missing with heartbeat = " +
                                               _heartbeat + " seconds");
        }
    }

    /**
     * Handles incoming control commands on channel zero.
     * @see ChannelN#processAsync
     */
    @SuppressWarnings("unused")
    public boolean processControlCommand(Command c) throws IOException
    {
        // Similar trick to ChannelN.processAsync used here, except
        // we're interested in whole-connection quiescing.

        // See the detailed comments in ChannelN.processAsync.

        Method method = c.getMethod();

        if (isOpen()) {
            if (method instanceof AMQP.Connection.Close) {
                handleConnectionClose(c);
                return true;
            } else if (method instanceof AMQP.Connection.Blocked) {
                AMQP.Connection.Blocked blocked = (AMQP.Connection.Blocked) method;
                try {
                    for (BlockedListener l : this.blockedListeners) {
                        l.handleBlocked(blocked.getReason());
                    }
                } catch (Throwable ex) {
                    getExceptionHandler().handleBlockedListenerException(this, ex);
                }
                return true;
            } else if (method instanceof AMQP.Connection.Unblocked) {
                try {
                    for (BlockedListener l : this.blockedListeners) {
                        l.handleUnblocked();
                    }
                } catch (Throwable ex) {
                    getExceptionHandler().handleBlockedListenerException(this, ex);
                }
                return true;
            } else {
                return false;
            }
        } else {
            if (method instanceof AMQP.Connection.Close) {
                // Already shutting down, so just send back a CloseOk.
                try {
                    _channel0.quiescingTransmit(new AMQP.Connection.CloseOk.Builder().build());
                } catch (IOException ignored) { } // ignore
                return true;
            } else if (method instanceof AMQP.Connection.CloseOk) {
                // It's our final "RPC". Time to shut down.
                _running = false;
                // If Close was sent from within the MainLoop we
                // will not have a continuation to return to, so
                // we treat this as processed in that case.
                return !_channel0.isOutstandingRpc();
            } else { // Ignore all others.
                return true;
            }
        }
    }

    private void handleConnectionClose(Command closeCommand) {
        ShutdownSignalException sse = shutdown(closeCommand.getMethod(), false, null, _inConnectionNegotiation);
        try {
            _channel0.quiescingTransmit(new AMQP.Connection.CloseOk.Builder().build());
        } catch (IOException ignored) { } // ignore
        _brokerInitiatedShutdown = true;
        SocketCloseWait scw = new SocketCloseWait(sse);

        // if shutdown executor is configured, use it. Otherwise
        // execute socket close monitor the old fashioned way.
        // see rabbitmq/rabbitmq-java-client#91
        if(shutdownExecutor != null) {
            shutdownExecutor.execute(scw);
        } else {
            final String name = "RabbitMQ connection shutdown monitor " +
                                    getHostAddress() + ":" + getPort();
            Thread waiter = Environment.newThread(threadFactory, scw, name);
            waiter.start();
        }
    }

    private class SocketCloseWait implements Runnable {
        // same as ConnectionFactory.DEFAULT_SHUTDOWN_TIMEOUT
        private long SOCKET_CLOSE_TIMEOUT = 10000;

        private final ShutdownSignalException cause;

        SocketCloseWait(ShutdownSignalException sse) {
            cause = sse;
        }

        @Override
        public void run() {
            try {
                // TODO: use a sensible timeout here
                _appContinuation.get(SOCKET_CLOSE_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
                // this releases the thread
            } finally {
                _running = false;
                    _channel0.notifyOutstandingRpc(cause);
            }
        }
    }

    /**
     * Protected API - causes all attached channels to terminate (shutdown) with a ShutdownSignal
     * built from the argument, and stops this connection from accepting further work from the
     * application. {@link com.rabbitmq.client.ShutdownListener ShutdownListener}s for the
     * connection are notified when the main loop terminates.
     * @param reason description of reason for the exception
     * @param initiatedByApplication true if caused by a client command
     * @param cause trigger exception which caused shutdown
     * @param notifyRpc true if outstanding rpc should be informed of shutdown
     * @return a shutdown signal built using the given arguments
     */
    public ShutdownSignalException shutdown(Method reason,
                         boolean initiatedByApplication,
                         Throwable cause,
                         boolean notifyRpc)
    {
        ShutdownSignalException sse = startShutdown(reason, initiatedByApplication, cause, notifyRpc);
        finishShutdown(sse);
        return sse;
    }

    private ShutdownSignalException startShutdown(Method reason,
                         boolean initiatedByApplication,
                         Throwable cause,
                         boolean notifyRpc)
    {
        ShutdownSignalException sse = new ShutdownSignalException(true,initiatedByApplication,
                                                                  reason, this);
        sse.initCause(cause);
        if (!setShutdownCauseIfOpen(sse)) {
            if (initiatedByApplication)
                throw new AlreadyClosedException(getCloseReason(), cause);
        }

        // stop any heartbeating
        _heartbeatSender.shutdown();

        _channel0.processShutdownSignal(sse, !initiatedByApplication, notifyRpc);
        return sse;
    }

    private void finishShutdown(ShutdownSignalException sse) {
        ChannelManager cm = _channelManager;
        if (cm != null) cm.handleSignal(sse);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void close()
        throws IOException
    {
        close(-1);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void close(int timeout)
        throws IOException
    {
        close(AMQP.REPLY_SUCCESS, "OK", timeout);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void close(int closeCode, String closeMessage)
        throws IOException
    {
        close(closeCode, closeMessage, -1);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void close(int closeCode, String closeMessage, int timeout)
        throws IOException
    {
        close(closeCode, closeMessage, true, null, timeout, false);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void abort()
    {
        abort(-1);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void abort(int closeCode, String closeMessage)
    {
       abort(closeCode, closeMessage, -1);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void abort(int timeout)
    {
        abort(AMQP.REPLY_SUCCESS, "OK", timeout);
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void abort(int closeCode, String closeMessage, int timeout)
    {
        try {
            close(closeCode, closeMessage, true, null, timeout, true);
        } catch (IOException ignored) { } // ignore
    }

    /**
     * Protected API - Delegates to {@link
     * #close(int,String,boolean,Throwable,int,boolean) the
     * six-argument close method}, passing -1 for the timeout, and
     * false for the abort flag.
     */
    public void close(int closeCode,
                      String closeMessage,
                      boolean initiatedByApplication,
                      Throwable cause)
        throws IOException
    {
        close(closeCode, closeMessage, initiatedByApplication, cause, -1, false);
    }

    // TODO: Make this private
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
        boolean sync = !(Thread.currentThread() == mainLoopThread);

        try {
            AMQP.Connection.Close reason =
                new AMQP.Connection.Close.Builder()
                    .replyCode(closeCode)
                    .replyText(closeMessage)
                .build();

            final ShutdownSignalException sse = startShutdown(reason, initiatedByApplication, cause, true);
            if(sync){
                BlockingRpcContinuation<AMQCommand> k = new BlockingRpcContinuation<AMQCommand>(){
                    @Override
                    public AMQCommand transformReply(AMQCommand command) {
                        AMQConnection.this.finishShutdown(sse);
                        return command;
                    }};

              _channel0.quiescingRpc(reason, k);
              k.getReply(timeout);
            } else {
              _channel0.quiescingTransmit(reason);
            }
        } catch (TimeoutException tte) {
            if (!abort) {
                ShutdownSignalException sse = new ShutdownSignalException(true, true, null, this);
                sse.initCause(cause);
                throw sse;
            }
        } catch (ShutdownSignalException | IOException sse) {
            if (!abort)
                throw sse;
        } finally {
            if(sync) _frameHandler.close();
        }
    }

    @Override public String toString() {
        final String virtualHost = "/".equals(_virtualHost) ? _virtualHost : "/" + _virtualHost;
        return "amqp://" + this.credentialsProvider.getUsername() + "@" + getHostAddress() + ":" + getPort() + virtualHost;
    }

    private String getHostAddress() {
        return getAddress() == null ? null : getAddress().getHostAddress();
    }

    @Override
    public void addBlockedListener(BlockedListener listener) {
        blockedListeners.add(listener);
    }

    @Override
    public BlockedListener addBlockedListener(BlockedCallback blockedCallback, UnblockedCallback unblockedCallback) {
        BlockedListener blockedListener = new BlockedListener() {

            @Override
            public void handleBlocked(String reason) throws IOException {
                blockedCallback.handle(reason);
            }

            @Override
            public void handleUnblocked() throws IOException {
                unblockedCallback.handle();
            }
        };
        this.addBlockedListener(blockedListener);
        return blockedListener;
    }

    @Override
    public boolean removeBlockedListener(BlockedListener listener) {
        return blockedListeners.remove(listener);
    }

    @Override
    public void clearBlockedListeners() {
        blockedListeners.clear();
    }

    /** Public API - {@inheritDoc} */
    @Override
    public String getId() {
        return id;
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    public int getChannelRpcTimeout() {
        return channelRpcTimeout;
    }

    public boolean willCheckRpcResponseType() {
        return channelShouldCheckRpcResponseType;
    }

    public TrafficListener getTrafficListener() {
        return trafficListener;
    }
}
