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

    Connection newConnection(Address[] addrs) throws IOException;

    Connection newConnection(ExecutorService executor, Address[] addrs)
        throws IOException;

    /**
     * Create a new broker connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    Connection newConnection() throws IOException;

    Connection newConnection(ExecutorService executor) throws IOException;

    String getHost();

    void setHost(String host);

    int getPort();

    void setPort(int port);

    String getUsername();

    void setUsername(String username);

    String getPassword();

    void setPassword(String password);

    String getVirtualHost();

    void setVirtualHost(String virtualHost);

    void setUri(URI uri)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException;

    void setUri(String uriString)
            throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException;

    int getRequestedChannelMax();

    void setRequestedChannelMax(int requestedChannelMax);

    int getRequestedFrameMax();

    void setRequestedFrameMax(int requestedFrameMax);

    int getRequestedHeartbeat();

    void setConnectionTimeout(int connectionTimeout);

    int getConnectionTimeout();

    void setRequestedHeartbeat(int requestedHeartbeat);

    Map<String, Object> getClientProperties();

    void setClientProperties(Map<String, Object> clientProperties);

    SaslConfig getSaslConfig();

    void setSaslConfig(SaslConfig saslConfig);

    SocketFactory getSocketFactory();

    void setSocketFactory(SocketFactory factory);

    @SuppressWarnings("unused")
    SocketConfigurator getSocketConfigurator();

    void setSocketConfigurator(SocketConfigurator socketConfigurator);

    void setSharedExecutor(ExecutorService executor);

    ThreadFactory getThreadFactory();

    void setThreadFactory(ThreadFactory threadFactory);

    ExceptionHandler getExceptionHandler();

    void setExceptionHandler(ExceptionHandler exceptionHandler);

    boolean isSSL();

    void useSslProtocol()
        throws NoSuchAlgorithmException, KeyManagementException;

    void useSslProtocol(String protocol)
            throws NoSuchAlgorithmException, KeyManagementException;

    void useSslProtocol(String protocol, TrustManager trustManager)
                throws NoSuchAlgorithmException, KeyManagementException;

    void useSslProtocol(SSLContext context);

    boolean isAutomaticRecoveryEnabled();

    void setAutomaticRecoveryEnabled(boolean automaticRecovery);

    @SuppressWarnings("unused")
    boolean isTopologyRecoveryEnabled();

    void setTopologyRecoveryEnabled(boolean topologyRecovery);

    int getNetworkRecoveryInterval();

    void setNetworkRecoveryInterval(int networkRecoveryInterval);
}
