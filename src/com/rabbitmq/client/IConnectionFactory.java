package com.rabbitmq.client;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Convenience "factory" class to facilitate opening a {@link Connection} to an AMQP broker.
 */
public interface IConnectionFactory {
    /** Default user name */
    String DEFAULT_USER = "guest";
    /** Default password */
    String DEFAULT_PASS = "guest";
    /** Default virtual host */
    String DEFAULT_VHOST = "/";
    /** Default maximum channel number;
     *  zero for unlimited */
    int    DEFAULT_CHANNEL_MAX = 0;
    /** Default maximum frame size;
     *  zero means no limit */
    int    DEFAULT_FRAME_MAX = 0;
    /** Default heart-beat interval;
     *  zero means no heart-beats */
    int    DEFAULT_HEARTBEAT = 0;
    /** The default host */
    String DEFAULT_HOST = "localhost";
    /** The default non-ssl port */
    int    DEFAULT_AMQP_PORT = AMQP.PROTOCOL.PORT;

    /**
     * Create a new broker connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    Connection newConnection(Address[] addrs) throws IOException;

    /**
     * Create a new broker connection
     * @param executor thread execution service for consumers on the connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    Connection newConnection(ExecutorService executor, Address[] addrs)
        throws IOException;

    /**
     * Create a new broker connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    Connection newConnection() throws IOException;

    /**
     * Create a new broker connection
     * @param executor thread execution service for consumers on the connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    Connection newConnection(ExecutorService executor) throws IOException;

    /** @return the default host to use for connections */
    String getHost();

    /** @param host the default host to use for connections */
    void setHost(String host);

    /** @return the default port to use for connections */
    int getPort();

    /**
     * Set the target port.
     * @param port the default port to use for connections
     */
    void setPort(int port);

    /**
     * Retrieve the user name.
     * @return the AMQP user name to use when connecting to the broker
     */
    String getUsername();

    /**
     * Set the user name.
     * @param username the AMQP user name to use when connecting to the broker
     */
    void setUsername(String username);

     /**
     * Retrieve the password.
     * @return the password to use when connecting to the broker
     */
    String getPassword();

    /**
     * Set the password.
     * @param password the password to use when connecting to the broker
     */
    void setPassword(String password);

    /**
     * Retrieve the virtual host.
     * @return the virtual host to use when connecting to the broker
     */
    String getVirtualHost();

    /**
     * Set the virtual host.
     * @param virtualHost the virtual host to use when connecting to the broker
     */
    void setVirtualHost(String virtualHost);

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
    void setUri(URI uri)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException;

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
    void setUri(String uriString)
            throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException;

    /**
     * Retrieve the requested maximum channel number
     * @return the initially requested maximum channel number; zero for unlimited
     */
    int getRequestedChannelMax();

    /**
     * Set the requested maximum channel number
     * @param requestedChannelMax initially requested maximum channel number; zero for unlimited
     */
    void setRequestedChannelMax(int requestedChannelMax);

    /**
     * Retrieve the requested maximum frame size
     * @return the initially requested maximum frame size, in octets; zero for unlimited
     */
    int getRequestedFrameMax();

    /**
     * Set the requested maximum frame size
     * @param requestedFrameMax initially requested maximum frame size, in octets; zero for unlimited
     */
    void setRequestedFrameMax(int requestedFrameMax);

    /**
     * Retrieve the requested heartbeat interval.
     * @return the initially requested heartbeat interval, in seconds; zero for none
     */
    int getRequestedHeartbeat();

        /**
     * Set the connection timeout.
     * @param connectionTimeout connection establishment timeout in milliseconds; zero for infinite
     */
    void setConnectionTimeout(int connectionTimeout);

    /**
     * Retrieve the connection timeout.
     * @return the connection timeout, in milliseconds; zero for infinite
     */
    int getConnectionTimeout();

    /**
     * Set the requested heartbeat.
     * @param requestedHeartbeat the initially requested heartbeat interval, in seconds; zero for none
     */
    void setRequestedHeartbeat(int requestedHeartbeat);

    /**
     * Retrieve the currently-configured table of client properties
     * that will be sent to the server during connection
     * startup. Clients may add, delete, and alter keys in this
     * table. Such changes will take effect when the next new
     * connection is started using this factory.
     * @return the map of client properties
     * @see #setClientProperties
     */
    Map<String, Object> getClientProperties();

    /**
     * Replace the table of client properties that will be sent to the
     * server during subsequent connection startups.
     * @param clientProperties the map of extra client properties
     * @see #getClientProperties
     */
    void setClientProperties(Map<String, Object> clientProperties);

    /**
     * Gets the sasl config to use when authenticating
     * @return the sasl config
     * @see com.rabbitmq.client.SaslConfig
     */
    SaslConfig getSaslConfig();

    /**
     * Sets the sasl config to use when authenticating
     * @param saslConfig
     * @see com.rabbitmq.client.SaslConfig
     */
    void setSaslConfig(SaslConfig saslConfig);

    /**
     * Retrieve the socket factory used to make connections with.
     */
    SocketFactory getSocketFactory();

    /**
     * Set the socket factory used to make connections with. Can be
     * used to enable SSL connections by passing in a
     * javax.net.ssl.SSLSocketFactory instance.
     *
     * @see #useSslProtocol
     */
    void setSocketFactory(SocketFactory factory);

    /**
     * Get the socket configurator.
     *
     * @see #setSocketConfigurator(SocketConfigurator)
     */
    @SuppressWarnings("unused")
    SocketConfigurator getSocketConfigurator();

    /**
     * Set the socket configurator. This gets a chance to "configure" a socket
     * before it has been opened. The default socket configurator disables
     * Nagle's algorithm.
     *
     * @param socketConfigurator the configurator to use
     */
    void setSocketConfigurator(SocketConfigurator socketConfigurator);

    /**
     * Set the executor to use by default for newly created connections.
     * All connections that use this executor share it.
     *
     * It's developer's responsibility to shut down the executor
     * when it is no longer needed.
     *
     * @param executor
     */
    void setSharedExecutor(ExecutorService executor);

    /**
     * Retrieve the thread factory used to instantiate new threads.
     * @see ThreadFactory
     */
    ThreadFactory getThreadFactory();

    /**
     * Set the thread factory used to instantiate new threads.
     * @see ThreadFactory
     */
    void setThreadFactory(ThreadFactory threadFactory);

    /**
    * Get the exception handler.
    *
    * @see com.rabbitmq.client.ExceptionHandler
    */
    ExceptionHandler getExceptionHandler();

    /**
     * Set the exception handler to use for newly created connections.
     * @see com.rabbitmq.client.ExceptionHandler
     */
    void setExceptionHandler(ExceptionHandler exceptionHandler);

    boolean isSSL();

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the DEFAULT_SSL_PROTOCOL and a trusting TrustManager.
     */
    void useSslProtocol()
        throws NoSuchAlgorithmException, KeyManagementException;

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the supplied protocol and a very trusting TrustManager.
     */
    void useSslProtocol(String protocol)
            throws NoSuchAlgorithmException, KeyManagementException;
    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in the SSL protocol to use, e.g. "TLS" or "SSLv3".
     *
     * @param protocol SSL protocol to use.
     */
    void useSslProtocol(String protocol, TrustManager trustManager)
                throws NoSuchAlgorithmException, KeyManagementException;

    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in an initialized SSLContext.
     *
     * @param context An initialized SSLContext
     */
    void useSslProtocol(SSLContext context);

    /**
     * Returns true if automatic connection recovery is enabled, false otherwise
     * @return true if automatic connection recovery is enabled, false otherwise
     */
    boolean isAutomaticRecoveryEnabled();

    /**
     * Enables or disables automatic connection recovery
     * @param automaticRecovery if true, enables connection recovery
     */
    void setAutomaticRecoveryEnabled(boolean automaticRecovery);

    /**
     * Returns true if topology recovery is enabled, false otherwise
     * @return true if topology recovery is enabled, false otherwise
     */
    @SuppressWarnings("unused")
    boolean isTopologyRecoveryEnabled();

    /**
     * Enables or disables topology recovery
     * @param topologyRecovery if true, enables topology recovery
     */
    void setTopologyRecoveryEnabled(boolean topologyRecovery);

    /**
     * Returns automatic connection recovery interval in milliseconds.
     * @return how long will automatic recovery wait before attempting to reconnect, in ms; default is 5000
     */
    int getNetworkRecoveryInterval();

    /**
     * Sets connection recovery interval. Default is 5000.
     * @param networkRecoveryInterval how long will automatic recovery wait before attempting to reconnect, in ms
     */
    void setNetworkRecoveryInterval(int networkRecoveryInterval);
}
