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
//  Copyright (c) 2007-2014 GoPivotal, Inc.  All rights reserved.
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
 * The standard {@link com.rabbitmq.client.IConnectionFactory} implementation.
 */
public class ConnectionFactory implements Cloneable, IConnectionFactory {

    /** Default Executor threads */
    @Deprecated
    public static final int    DEFAULT_NUM_CONSUMER_THREADS = 5;
    /** 'Use the default port' port */
    public static final int    USE_DEFAULT_PORT = -1;
    /** The default ssl port */
    public static final int    DEFAULT_AMQP_OVER_SSL_PORT = 5671;
    /** The default connection timeout;
     *  zero means wait indefinitely */
    public static final int    DEFAULT_CONNECTION_TIMEOUT = 0;

    /** The default SSL protocol */
    private static final String DEFAULT_SSL_PROTOCOL = "SSLv3";

    private String username                       = DEFAULT_USER;
    private String password                       = DEFAULT_PASS;
    private String virtualHost                    = DEFAULT_VHOST;
    private String host                           = DEFAULT_HOST;
    private int port                              = USE_DEFAULT_PORT;
    private int requestedChannelMax               = DEFAULT_CHANNEL_MAX;
    private int requestedFrameMax                 = DEFAULT_FRAME_MAX;
    private int requestedHeartbeat                = DEFAULT_HEARTBEAT;
    private int connectionTimeout                 = DEFAULT_CONNECTION_TIMEOUT;
    private Map<String, Object> _clientProperties = AMQConnection.defaultClientProperties();
    private SocketFactory factory                 = SocketFactory.getDefault();
    private SaslConfig saslConfig                 = DefaultSaslConfig.PLAIN;
    private ExecutorService sharedExecutor;
    private ThreadFactory threadFactory           = Executors.defaultThreadFactory();
    private SocketConfigurator socketConf         = new DefaultSocketConfigurator();
    private ExceptionHandler exceptionHandler     = new DefaultExceptionHandler();

    private boolean automaticRecovery             = false;
    private boolean topologyRecovery              = true;

    private int networkRecoveryInterval           = 5000;

    /** @return number of consumer threads in default {@link ExecutorService} */
    @Deprecated
    public int getNumConsumerThreads() {
        return DEFAULT_NUM_CONSUMER_THREADS;
    }

    /** @param numConsumerThreads threads in created private executor service */
    @Deprecated
    public void setNumConsumerThreads(int numConsumerThreads) {
        throw new IllegalArgumentException("setNumConsumerThreads not supported -- create explicit ExecutorService instead.");
    }

    /** {@inheritDoc} */
    @Override
    public String getHost() {
        return host;
    }

    /** {@inheritDoc} */
    @Override
    public void setHost(String host) {
        this.host = host;
    }

    public static int portOrDefault(int port, boolean ssl) {
        if (port != USE_DEFAULT_PORT) return port;
        else if (ssl) return DEFAULT_AMQP_OVER_SSL_PORT;
        else return DEFAULT_AMQP_PORT;
    }

    /** {@inheritDoc} */
    @Override
    public int getPort() {
        return portOrDefault(port, isSSL());
    }

    /** {@inheritDoc} */
    @Override
    public void setPort(int port) {
        this.port = port;
    }

    /** {@inheritDoc} */
    @Override
    public String getUsername() {
        return this.username;
    }

    /** {@inheritDoc} */
    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    /** {@inheritDoc} */
    @Override
    public String getPassword() {
        return this.password;
    }

    /** {@inheritDoc} */
    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    /** {@inheritDoc} */
    @Override
    public String getVirtualHost() {
        return this.virtualHost;
    }

    /** {@inheritDoc} */
    @Override
    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
    public int getRequestedChannelMax() {
        return this.requestedChannelMax;
    }

    /** {@inheritDoc} */
    @Override
    public void setRequestedChannelMax(int requestedChannelMax) {
        this.requestedChannelMax = requestedChannelMax;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestedFrameMax() {
        return this.requestedFrameMax;
    }

    /** {@inheritDoc} */
    @Override
    public void setRequestedFrameMax(int requestedFrameMax) {
        this.requestedFrameMax = requestedFrameMax;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestedHeartbeat() {
        return this.requestedHeartbeat;
    }

    /** {@inheritDoc} */
    @Override
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /** {@inheritDoc} */
    @Override
    public int getConnectionTimeout() {
        return this.connectionTimeout;
    }

    /** {@inheritDoc} */
    @Override
    public void setRequestedHeartbeat(int requestedHeartbeat) {
        this.requestedHeartbeat = requestedHeartbeat;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getClientProperties() {
        return _clientProperties;
    }

    /** {@inheritDoc} */
    @Override
    public void setClientProperties(Map<String, Object> clientProperties) {
        _clientProperties = clientProperties;
    }

    /** {@inheritDoc} */
    @Override
    public SaslConfig getSaslConfig() {
        return saslConfig;
    }

    /** {@inheritDoc} */
    @Override
    public void setSaslConfig(SaslConfig saslConfig) {
        this.saslConfig = saslConfig;
    }

    /** {@inheritDoc} */
    @Override
    public SocketFactory getSocketFactory() {
        return this.factory;
    }

    /** {@inheritDoc} */
    @Override
    public void setSocketFactory(SocketFactory factory) {
        this.factory = factory;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unused")
    public SocketConfigurator getSocketConfigurator() {
        return socketConf;
    }

    /** {@inheritDoc} */
    @Override
    public void setSocketConfigurator(SocketConfigurator socketConfigurator) {
        this.socketConf = socketConfigurator;
    }

    /** {@inheritDoc} */
    @Override
    public void setSharedExecutor(ExecutorService executor) {
        this.sharedExecutor = executor;
    }

    /** {@inheritDoc} */
    @Override
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /** {@inheritDoc} */
    @Override
    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /** {@inheritDoc} */
    @Override
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /** {@inheritDoc} */
    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        if (exceptionHandler == null) {
          throw new IllegalArgumentException("exception handler cannot be null!");
        }
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public boolean isSSL(){
        return getSocketFactory() instanceof SSLSocketFactory;
    }

    /** {@inheritDoc} */
    @Override
    public void useSslProtocol()
        throws NoSuchAlgorithmException, KeyManagementException
    {
        useSslProtocol(DEFAULT_SSL_PROTOCOL);
    }

    /** {@inheritDoc} */
    @Override
    public void useSslProtocol(String protocol)
        throws NoSuchAlgorithmException, KeyManagementException
    {
        useSslProtocol(protocol, new NullTrustManager());
    }

    /** {@inheritDoc} */
    @Override
    public void useSslProtocol(String protocol, TrustManager trustManager)
        throws NoSuchAlgorithmException, KeyManagementException
    {
        SSLContext c = SSLContext.getInstance(protocol);
        c.init(null, new TrustManager[] { trustManager }, null);
        useSslProtocol(c);
    }

    /** {@inheritDoc} */
    @Override
    public void useSslProtocol(SSLContext context)
    {
        setSocketFactory(context.getSocketFactory());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAutomaticRecoveryEnabled() {
        return automaticRecovery;
    }

    /** {@inheritDoc} */
    @Override
    public void setAutomaticRecoveryEnabled(boolean automaticRecovery) {
        this.automaticRecovery = automaticRecovery;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unused")
    public boolean isTopologyRecoveryEnabled() {
        return topologyRecovery;
    }

    /** {@inheritDoc} */
    @Override
    public void setTopologyRecoveryEnabled(boolean topologyRecovery) {
        this.topologyRecovery = topologyRecovery;
    }

    protected FrameHandlerFactory createFrameHandlerFactory() throws IOException {
        return new FrameHandlerFactory(connectionTimeout, factory, socketConf, isSSL());
    }

    /** {@inheritDoc} */
    @Override
    public Connection newConnection(Address[] addrs) throws IOException {
        return newConnection(this.sharedExecutor, addrs);
    }

    /** {@inheritDoc} */
    @Override
    public Connection newConnection(ExecutorService executor, Address[] addrs)
        throws IOException
    {
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
        return new ConnectionParams(username, password, executor, virtualHost, getClientProperties(),
                                    requestedFrameMax, requestedChannelMax, requestedHeartbeat, saslConfig,
                                    networkRecoveryInterval, topologyRecovery, exceptionHandler, threadFactory);
    }

    /** {@inheritDoc} */
    @Override
    public Connection newConnection() throws IOException {
        return newConnection(this.sharedExecutor,
                             new Address[] {new Address(getHost(), getPort())}
                            );
    }

    /** {@inheritDoc} */
    @Override
    public Connection newConnection(ExecutorService executor) throws IOException {
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

    /** {@inheritDoc} */
    @Override
    public int getNetworkRecoveryInterval() {
        return networkRecoveryInterval;
    }

    /** {@inheritDoc} */
    @Override
    public void setNetworkRecoveryInterval(int networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }
}
