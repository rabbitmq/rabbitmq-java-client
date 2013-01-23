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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;

/**
 * Convenience "factory" class to facilitate opening a {@link Connection} to an AMQP broker.
 */

public class ConnectionFactory implements Cloneable {

    /** Default Executor threads */
    @Deprecated
    public static final int    DEFAULT_NUM_CONSUMER_THREADS = 5;
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
     *  zero means no heart-beats */
    public static final int    DEFAULT_HEARTBEAT = 0;
    /** The default host */
    public static final String DEFAULT_HOST = "localhost";
    /** 'Use the default port' port */
    public static final int    USE_DEFAULT_PORT = -1;
    /** The default non-ssl port */
    public static final int    DEFAULT_AMQP_PORT = AMQP.PROTOCOL.PORT;
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

    /** @return the default host to use for connections */
    public String getHost() {
        return host;
    }

    /** @param host the default host to use for connections */
    public void setHost(String host) {
        this.host = host;
    }

    private int portOrDefault(int port){
        if(port != USE_DEFAULT_PORT) return port;
        else if(isSSL()) return DEFAULT_AMQP_OVER_SSL_PORT;
        else return DEFAULT_AMQP_PORT;
    }

    /** @return the default port to use for connections */
    public int getPort() {
        return portOrDefault(port);
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
     * Set the connection timeout.
     * @param connectionTimeout connection establishment timeout in milliseconds; zero for infinite
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Retrieve the connection timeout.
     * @return the connection timeout, in milliseconds; zero for infinite
     */
    public int getConnectionTimeout() {
        return this.connectionTimeout;
    }

    /**
     * Set the requested heartbeat.
     * @param requestedHeartbeat the initially requested heartbeat interval, in seconds; zero for none
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
     * Pass in the SSL protocol to use, e.g. "TLS" or "SSLv3".
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
    public void useSslProtocol(SSLContext context)
    {
        setSocketFactory(context.getSocketFactory());
    }

    protected FrameHandler createFrameHandler(Address addr)
        throws IOException {

        String hostName = addr.getHost();
        int portNumber = portOrDefault(addr.getPort());
        Socket socket = null;
        try {
            socket = factory.createSocket();
            configureSocket(socket);
            socket.connect(new InetSocketAddress(hostName, portNumber),
                    connectionTimeout);
            return createFrameHandler(socket);
        } catch (IOException ioe) {
            quietTrySocketClose(socket);
            throw ioe;
        }
    }

    private static void quietTrySocketClose(Socket socket) {
        if (socket != null)
            try { socket.close(); } catch (Exception _) {/*ignore exceptions*/}
    }

    protected FrameHandler createFrameHandler(Socket sock)
        throws IOException
    {
        return new SocketFrameHandler(sock);
    }

    /**
     *  Provides a hook to insert custom configuration of the sockets
     *  used to connect to an AMQP server before they connect.
     *
     *  The default behaviour of this method is to disable Nagle's
     *  algorithm to get more consistently low latency.  However it
     *  may be overridden freely and there is no requirement to retain
     *  this behaviour.
     *
     *  @param socket The socket that is to be used for the Connection
     */
    protected void configureSocket(Socket socket) throws IOException{
        // disable Nagle's algorithm, for more consistently low latency
        socket.setTcpNoDelay(true);
    }

    /**
     * Create a new broker connection
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(Address[] addrs) throws IOException {
        return newConnection(null, addrs);
    }

    /**
     * Create a new broker connection
     * @param executor thread execution service for consumers on the connection
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(ExecutorService executor, Address[] addrs)
        throws IOException
    {
        IOException lastException = null;
        for (Address addr : addrs) {
            try {
                FrameHandler frameHandler = createFrameHandler(addr);
                AMQConnection conn =
                    new AMQConnection(username,
                                      password,
                                      frameHandler,
                                      executor,
                                      virtualHost,
                                      getClientProperties(),
                                      requestedFrameMax,
                                      requestedChannelMax,
                                      requestedHeartbeat,
                                      saslConfig);
                conn.start();
                return conn;
            } catch (IOException e) {
                lastException = e;
            }
        }

        throw (lastException != null) ? lastException
                                      : new IOException("failed to connect");
    }

    /**
     * Create a new broker connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection() throws IOException {
        return newConnection(null,
                             new Address[] {new Address(getHost(), getPort())}
                            );
    }

    /**
     * Create a new broker connection
     * @param executor thread execution service for consumers on the connection
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
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
}
