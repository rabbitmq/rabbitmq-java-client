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

package com.rabbitmq.client;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;

/**
 * Convenience "factory" class to facilitate opening a {@link Connection} to an AMQP broker.
 */

public class ConnectionFactory implements Cloneable {

    /** Default user name */
    public static final String DEFAULT_USER = "guest";
    /** Default password */
    public static final String DEFAULT_PASS = "guest";
    /** Default virtual host */
    public static final String DEFAULT_VHOST = "/";
    /** Default maximum channel number;
     *  zero for unlimited */
    public static final int    DEFAULT_CHANNEL_MAX = 0;
    /** Default maximum frame size;
     *  zero means no limit */
    public static final int    DEFAULT_FRAME_MAX = 0;
    /** Default heart-beat interval;
     *  60 seconds */
    public static final int    DEFAULT_HEARTBEAT = 60;
    /** The default host */
    public static final String DEFAULT_HOST = "localhost";
    /** 'Use the default port' port */
    public static final int    USE_DEFAULT_PORT = -1;
    /** The default non-ssl port */
    public static final int    DEFAULT_AMQP_PORT = AMQP.PROTOCOL.PORT;
    /** The default ssl port */
    public static final int    DEFAULT_AMQP_OVER_SSL_PORT = 5671;
    /** The default TCP connection timeout: 60 seconds */
    public static final int    DEFAULT_CONNECTION_TIMEOUT = 60000;
    /**
     * The default AMQP 0-9-1 connection handshake timeout. See DEFAULT_CONNECTION_TIMEOUT
     * for TCP (socket) connection timeout.
     */
    public static final int    DEFAULT_HANDSHAKE_TIMEOUT = 10000;
    /** The default shutdown timeout;
     *  zero means wait indefinitely */
    public static final int    DEFAULT_SHUTDOWN_TIMEOUT = 10000;

    /** The default SSL protocol */
    private static final String DEFAULT_SSL_PROTOCOL = "TLSv1";

    private String username                       = DEFAULT_USER;
    private String password                       = DEFAULT_PASS;
    private String virtualHost                    = DEFAULT_VHOST;
    private String host                           = DEFAULT_HOST;
    private int port                              = USE_DEFAULT_PORT;
    private int requestedChannelMax               = DEFAULT_CHANNEL_MAX;
    private int requestedFrameMax                 = DEFAULT_FRAME_MAX;
    private int requestedHeartbeat                = DEFAULT_HEARTBEAT;
    private int connectionTimeout                 = DEFAULT_CONNECTION_TIMEOUT;
    private int handshakeTimeout                  = DEFAULT_HANDSHAKE_TIMEOUT;
    private int shutdownTimeout                   = DEFAULT_SHUTDOWN_TIMEOUT;
    private Map<String, Object> _clientProperties = AMQConnection.defaultClientProperties();
    private SocketFactory factory                 = SocketFactory.getDefault();
    private SaslConfig saslConfig                 = DefaultSaslConfig.PLAIN;
    private ExecutorService sharedExecutor;
    private ThreadFactory threadFactory           = Executors.defaultThreadFactory();
    private SocketConfigurator socketConf         = new DefaultSocketConfigurator();
    private ExceptionHandler exceptionHandler     = new DefaultExceptionHandler();

    private boolean automaticRecovery             = false;
    private boolean topologyRecovery              = true;

    // long is used to make sure the users can use both ints
    // and longs safely. It is unlikely that anybody'd need
    // to use recovery intervals > Integer.MAX_VALUE in practice.
    private long networkRecoveryInterval          = 5000;

    /** @return the default host to use for connections */
    public String getHost() {
        return host;
    }

    /** @param host the default host to use for connections */
    public void setHost(String host) {
        this.host = host;
    }

    public static int portOrDefault(int port, boolean ssl) {
        if (port != USE_DEFAULT_PORT) return port;
        else if (ssl) return DEFAULT_AMQP_OVER_SSL_PORT;
        else return DEFAULT_AMQP_PORT;
    }

    /** @return the default port to use for connections */
    public int getPort() {
        return portOrDefault(port, isSSL());
    }

    /**
     * Set the target port.
     * @param port the default port to use for connections
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Retrieve the user name.
     * @return the AMQP user name to use when connecting to the broker
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Set the user name.
     * @param username the AMQP user name to use when connecting to the broker
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Retrieve the password.
     * @return the password to use when connecting to the broker
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Set the password.
     * @param password the password to use when connecting to the broker
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Retrieve the virtual host.
     * @return the virtual host to use when connecting to the broker
     */
    public String getVirtualHost() {
        return this.virtualHost;
    }

    /**
     * Set the virtual host.
     * @param virtualHost the virtual host to use when connecting to the broker
     */
    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }


    /**
     * Convenience method for setting the fields in an AMQP URI: host,
     * port, username, password and virtual host.  If any part of the
     * URI is ommited, the ConnectionFactory's corresponding variable
     * is left unchanged.
     * @param uri is the AMQP URI containing the data
     */
    public void setUri(URI uri)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        if ("amqp".equals(uri.getScheme().toLowerCase())) {
            // nothing special to do
        } else if ("amqps".equals(uri.getScheme().toLowerCase())) {
            setPort(DEFAULT_AMQP_OVER_SSL_PORT);
            useSslProtocol();
        } else {
            throw new IllegalArgumentException("Wrong scheme in AMQP URI: " +
                                               uri.getScheme());
        }

        String host = uri.getHost();
        if (host != null) {
            setHost(host);
        }

        int port = uri.getPort();
        if (port != -1) {
            setPort(port);
        }

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            String userPass[] = userInfo.split(":");
            if (userPass.length > 2) {
                throw new IllegalArgumentException("Bad user info in AMQP " +
                                                   "URI: " + userInfo);
            }

            setUsername(uriDecode(userPass[0]));
            if (userPass.length == 2) {
                setPassword(uriDecode(userPass[1]));
            }
        }

        String path = uri.getRawPath();
        if (path != null && path.length() > 0) {
            if (path.indexOf('/', 1) != -1) {
                throw new IllegalArgumentException("Multiple segments in " +
                                                   "path of AMQP URI: " +
                                                   path);
            }

            setVirtualHost(uriDecode(uri.getPath().substring(1)));
        }
    }

    /**
     * Convenience method for setting the fields in an AMQP URI: host,
     * port, username, password and virtual host.  If any part of the
     * URI is ommited, the ConnectionFactory's corresponding variable
     * is left unchanged.  Note that not all valid AMQP URIs are
     * accepted; in particular, the hostname must be given if the
     * port, username or password are given, and escapes in the
     * hostname are not permitted.
     * @param uriString is the AMQP URI containing the data
     */
    public void setUri(String uriString)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        setUri(new URI(uriString));
    }

    private String uriDecode(String s) {
        try {
            // URLDecode decodes '+' to a space, as for
            // form encoding.  So protect plus signs.
            return URLDecoder.decode(s.replace("+", "%2B"), "US-ASCII");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve the requested maximum channel number
     * @return the initially requested maximum channel number; zero for unlimited
     */
    public int getRequestedChannelMax() {
        return this.requestedChannelMax;
    }

    /**
     * Set the requested maximum channel number
     * @param requestedChannelMax initially requested maximum channel number; zero for unlimited
     */
    public void setRequestedChannelMax(int requestedChannelMax) {
        this.requestedChannelMax = requestedChannelMax;
    }

    /**
     * Retrieve the requested maximum frame size
     * @return the initially requested maximum frame size, in octets; zero for unlimited
     */
    public int getRequestedFrameMax() {
        return this.requestedFrameMax;
    }

    /**
     * Set the requested maximum frame size
     * @param requestedFrameMax initially requested maximum frame size, in octets; zero for unlimited
     */
    public void setRequestedFrameMax(int requestedFrameMax) {
        this.requestedFrameMax = requestedFrameMax;
    }

    /**
     * Retrieve the requested heartbeat interval.
     * @return the initially requested heartbeat interval, in seconds; zero for none
     */
    public int getRequestedHeartbeat() {
        return this.requestedHeartbeat;
    }

    /**
     * Set the TCP connection timeout.
     * @param timeout connection TCP establishment timeout in milliseconds; zero for infinite
     */
    public void setConnectionTimeout(int timeout) {
        if(timeout < 0) {
            throw new IllegalArgumentException("TCP connection timeout cannot be negative");
        }
        this.connectionTimeout = timeout;
    }

    /**
     * Retrieve the TCP connection timeout.
     * @return the TCP connection timeout, in milliseconds; zero for infinite
     */
    public int getConnectionTimeout() {
        return this.connectionTimeout;
    }

    /**
     * Retrieve the AMQP 0-9-1 protocol handshake timeout.
     * @return the AMQP0-9-1 protocol handshake timeout, in milliseconds
     */
    public int getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * Set the AMQP0-9-1 protocol handshake timeout.
     * @param timeout the AMQP0-9-1 protocol handshake timeout, in milliseconds
     */
    public void setHandshakeTimeout(int timeout) {
        if(timeout < 0) {
            throw new IllegalArgumentException("handshake timeout cannot be negative");
        }
        this.handshakeTimeout = timeout;
    }

    /**
     * Set the shutdown timeout. This is the amount of time that Consumer implementations have to
     * continue working through deliveries (and other Consumer callbacks) <b>after</b> the connection
     * has closed but before the ConsumerWorkService is torn down. If consumers exceed this timeout
     * then any remaining queued deliveries (and other Consumer callbacks, <b>including</b>
     * the Consumer's handleShutdownSignal() invocation) will be lost.
     * @param shutdownTimeout shutdown timeout in milliseconds; zero for infinite; default 10000
     */
    public void setShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Retrieve the shutdown timeout.
     * @return the shutdown timeout, in milliseconds; zero for infinite
     */
    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Set the requested heartbeat timeout. Heartbeat frames will be sent at about 1/2 the timeout interval.
     * @param requestedHeartbeat the initially requested heartbeat timeout, in seconds; zero for none
     * @see <a href="http://rabbitmq.com/heartbeats.html">RabbitMQ Heartbeats Guide</a>
     */
    public void setRequestedHeartbeat(int requestedHeartbeat) {
        this.requestedHeartbeat = requestedHeartbeat;
    }

    /**
     * Retrieve the currently-configured table of client properties
     * that will be sent to the server during connection
     * startup. Clients may add, delete, and alter keys in this
     * table. Such changes will take effect when the next new
     * connection is started using this factory.
     * @return the map of client properties
     * @see #setClientProperties
     */
    public Map<String, Object> getClientProperties() {
        return _clientProperties;
    }

    /**
     * Replace the table of client properties that will be sent to the
     * server during subsequent connection startups.
     * @param clientProperties the map of extra client properties
     * @see #getClientProperties
     */
    public void setClientProperties(Map<String, Object> clientProperties) {
        _clientProperties = clientProperties;
    }

    /**
     * Gets the sasl config to use when authenticating
     * @return the sasl config
     * @see com.rabbitmq.client.SaslConfig
     */
    public SaslConfig getSaslConfig() {
        return saslConfig;
    }

    /**
     * Sets the sasl config to use when authenticating
     * @param saslConfig
     * @see com.rabbitmq.client.SaslConfig
     */
    public void setSaslConfig(SaslConfig saslConfig) {
        this.saslConfig = saslConfig;
    }

    /**
     * Retrieve the socket factory used to make connections with.
     */
    public SocketFactory getSocketFactory() {
        return this.factory;
    }

    /**
     * Set the socket factory used to make connections with. Can be
     * used to enable SSL connections by passing in a
     * javax.net.ssl.SSLSocketFactory instance.
     *
     * @see #useSslProtocol
     */
    public void setSocketFactory(SocketFactory factory) {
        this.factory = factory;
    }

    /**
     * Get the socket configurator.
     *
     * @see #setSocketConfigurator(SocketConfigurator)
     */
    @SuppressWarnings("unused")
    public SocketConfigurator getSocketConfigurator() {
        return socketConf;
    }

    /**
     * Set the socket configurator. This gets a chance to "configure" a socket
     * before it has been opened. The default socket configurator disables
     * Nagle's algorithm.
     *
     * @param socketConfigurator the configurator to use
     */
    public void setSocketConfigurator(SocketConfigurator socketConfigurator) {
        this.socketConf = socketConfigurator;
    }

    /**
     * Set the executor to use by default for newly created connections.
     * All connections that use this executor share it.
     *
     * It's developer's responsibility to shut down the executor
     * when it is no longer needed.
     *
     * @param executor
     */
    public void setSharedExecutor(ExecutorService executor) {
        this.sharedExecutor = executor;
    }

    /**
     * Retrieve the thread factory used to instantiate new threads.
     * @see ThreadFactory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Set the thread factory used to instantiate new threads.
     * @see ThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
    * Get the exception handler.
    *
    * @see com.rabbitmq.client.ExceptionHandler
    */
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Set the exception handler to use for newly created connections.
     * @see com.rabbitmq.client.ExceptionHandler
     */
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        if (exceptionHandler == null) {
          throw new IllegalArgumentException("exception handler cannot be null!");
        }
        this.exceptionHandler = exceptionHandler;
    }

    public boolean isSSL(){
        return getSocketFactory() instanceof SSLSocketFactory;
    }

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the DEFAULT_SSL_PROTOCOL and a trusting TrustManager.
     */
    public void useSslProtocol()
        throws NoSuchAlgorithmException, KeyManagementException
    {
        useSslProtocol(DEFAULT_SSL_PROTOCOL);
    }

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the supplied protocol and a very trusting TrustManager.
     */
    public void useSslProtocol(String protocol)
        throws NoSuchAlgorithmException, KeyManagementException
    {
        useSslProtocol(protocol, new NullTrustManager());
    }

    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in the SSL protocol to use, e.g. "TLSv1" or "TLSv1.2".
     *
     * @param protocol SSL protocol to use.
     */
    public void useSslProtocol(String protocol, TrustManager trustManager)
        throws NoSuchAlgorithmException, KeyManagementException
    {
        SSLContext c = SSLContext.getInstance(protocol);
        c.init(null, new TrustManager[] { trustManager }, null);
        useSslProtocol(c);
    }

    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in an initialized SSLContext.
     *
     * @param context An initialized SSLContext
     */
    public void useSslProtocol(SSLContext context) {
        setSocketFactory(context.getSocketFactory());
    }

    /**
     * Returns true if <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>
     * is enabled, false otherwise
     * @return true if automatic connection recovery is enabled, false otherwise
     * @see <a href="http://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
     */
    public boolean isAutomaticRecoveryEnabled() {
        return automaticRecovery;
    }

    /**
     * Enables or disables <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>.
     * @param automaticRecovery if true, enables connection recovery
     * @see <a href="http://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
     */
    public void setAutomaticRecoveryEnabled(boolean automaticRecovery) {
        this.automaticRecovery = automaticRecovery;
    }

    /**
     * Returns true if topology recovery is enabled, false otherwise
     * @return true if topology recovery is enabled, false otherwise
     * @see <a href="http://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
     */
    @SuppressWarnings("unused")
    public boolean isTopologyRecoveryEnabled() {
        return topologyRecovery;
    }

    /**
     * Enables or disables topology recovery
     * @param topologyRecovery if true, enables topology recovery
     * @see <a href="http://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
     */
    public void setTopologyRecoveryEnabled(boolean topologyRecovery) {
        this.topologyRecovery = topologyRecovery;
    }

    protected FrameHandlerFactory createFrameHandlerFactory() throws IOException {
        return new FrameHandlerFactory(connectionTimeout, factory, socketConf, isSSL());
    }

    /**
     * Create a new broker connection, picking the first available address from
     * the list.
     *
     * If <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>
     * is enabled, the connection returned by this method will be {@link Recoverable}. Future
     * reconnection attempts will pick a random accessible address from the provided list.
     *
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(Address[] addrs) throws IOException, TimeoutException {
        return newConnection(this.sharedExecutor, addrs);
    }

    /**
     * Create a new broker connection, picking the first available address from
     * the list.
     *
     * If <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>
     * is enabled, the connection returned by this method will be {@link Recoverable}. Future
     * reconnection attempts will pick a random accessible address from the provided list.
     *
     * @param executor thread execution service for consumers on the connection
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @return an interface to the connection
     * @throws java.io.IOException if it encounters a problem
     * @see <a href="http://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
     */
    public Connection newConnection(ExecutorService executor, Address[] addrs)
            throws IOException, TimeoutException {
        FrameHandlerFactory fhFactory = createFrameHandlerFactory();
        ConnectionParams params = params(executor);

        if (isAutomaticRecoveryEnabled()) {
            // see com.rabbitmq.client.impl.recovery.RecoveryAwareAMQConnectionFactory#newConnection
            AutorecoveringConnection conn = new AutorecoveringConnection(params, fhFactory, addrs);
            conn.init();
            return conn;
        } else {
            IOException lastException = null;
            for (Address addr : addrs) {
                try {
                    FrameHandler handler = fhFactory.create(addr);
                    AMQConnection conn = new AMQConnection(params, handler);
                    conn.start();
                    return conn;
                } catch (IOException e) {
                    lastException = e;
                }
            }
            throw (lastException != null) ? lastException : new IOException("failed to connect");
        }
    }

    public ConnectionParams params(ExecutorService executor) {
        // TODO: switch to use setters for all fields
        ConnectionParams result = new ConnectionParams(username, password, executor, virtualHost, getClientProperties(),
            requestedFrameMax, requestedChannelMax, requestedHeartbeat, shutdownTimeout, saslConfig,
            networkRecoveryInterval, topologyRecovery, exceptionHandler, threadFactory);
        result.setHandshakeTimeout(handshakeTimeout);
        return result;
    }

    /**
     * Create a new broker connection.
     *
     * If <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>
     * is enabled, the connection returned by this method will be {@link Recoverable}. Reconnection
     * attempts will always use the address configured on {@link ConnectionFactory}.
     *
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection() throws IOException, TimeoutException {
        return newConnection(this.sharedExecutor,
                             new Address[] {new Address(getHost(), getPort())}
                            );
    }

    /**
     * Create a new broker connection.
     *
     * If <a href="http://www.rabbitmq.com/api-guide.html#recovery">automatic connection recovery</a>
     * is enabled, the connection returned by this method will be {@link Recoverable}. Reconnection
     * attempts will always use the address configured on {@link ConnectionFactory}.
     *
     * @param executor thread execution service for consumers on the connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(ExecutorService executor) throws IOException, TimeoutException {
        return newConnection(executor,
                             new Address[] {new Address(getHost(), getPort())}
                            );
    }

    @Override public ConnectionFactory clone(){
        try {
            return (ConnectionFactory)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    /**
     * Returns automatic connection recovery interval in milliseconds.
     * @return how long will automatic recovery wait before attempting to reconnect, in ms; default is 5000
     */
    public long getNetworkRecoveryInterval() {
        return networkRecoveryInterval;
    }

    /**
     * Sets connection recovery interval. Default is 5000.
     * @param networkRecoveryInterval how long will automatic recovery wait before attempting to reconnect, in ms
     */
    public void setNetworkRecoveryInterval(int networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }

    /**
     * Sets connection recovery interval. Default is 5000.
     * @param networkRecoveryInterval how long will automatic recovery wait before attempting to reconnect, in ms
     */
    public void setNetworkRecoveryInterval(long networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }
}
