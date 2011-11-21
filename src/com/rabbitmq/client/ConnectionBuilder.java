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
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.

package com.rabbitmq.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;

/**
 * Builder class for {@link Connection}s.
 */
public class ConnectionBuilder {

    public static final int    DEFAULT_NUM_CONSUMER_THREADS = 5;
    public static final String DEFAULT_USER = "guest";
    public static final String DEFAULT_PASS = "guest";
    public static final String DEFAULT_VHOST = "/";
    public static final int    DEFAULT_CHANNEL_MAX = 0;
    public static final int    DEFAULT_FRAME_MAX = 0;
    public static final int    DEFAULT_HEARTBEAT = 0;
    public static final String DEFAULT_HOST = "localhost";
    public static final int    DEFAULT_AMQP_PORT = AMQP.PROTOCOL.PORT;
    public static final int    DEFAULT_AMQP_OVER_SSL_PORT = 5671;
    public static final int    DEFAULT_CONNECTION_TIMEOUT = 0;
    public static final String DEFAULT_SSL_PROTOCOL = "SSLv3";

    private int channelMax                       = DEFAULT_CHANNEL_MAX;
    private Map<String, Object> clientProperties = AMQConnection.defaultClientProperties();
    private int connectionTimeout                = DEFAULT_CONNECTION_TIMEOUT;
    private ExecutorService executor             = null;
    private int frameMax                         = DEFAULT_FRAME_MAX;
    private int heartbeat                        = DEFAULT_HEARTBEAT;
    private String host                          = DEFAULT_HOST;
    private int numConsumerThreads               = DEFAULT_NUM_CONSUMER_THREADS;
    private String password                      = DEFAULT_PASS;
    private int port                             = DEFAULT_AMQP_PORT;
    private boolean portSet                      = false; //indicates whether to use default
    private SaslConfig saslConfig                = DefaultSaslConfig.PLAIN;
    private SocketFactory socketFactory          = SocketFactory.getDefault();
    private String username                      = DEFAULT_USER;
    private String virtualHost                   = DEFAULT_VHOST;

    public ConnectionBuilder channelMax(int channelMax)
    {
        this.channelMax = channelMax; return this;
    }
    public ConnectionBuilder clientProperties(Map<String, Object> clientProperties)
    {
        this.clientProperties = clientProperties; return this;
    }
    public ConnectionBuilder clientProperty(String key, Object value)
    {
        this.clientProperties.put(key, value); return this;
    }
    public ConnectionBuilder connectionTimeout(int connectionTimeout)
    {
        this.connectionTimeout = connectionTimeout; return this;
    }
    public ConnectionBuilder executor(ExecutorService executor)
    {
        this.executor = executor; return this;
    }
    public ConnectionBuilder frameMax(int frameMax)
    {
        this.frameMax = frameMax; return this;
    }
    public ConnectionBuilder heartbeat(int heartbeat)
    {
        this.heartbeat = heartbeat; return this;
    }
    public ConnectionBuilder host(String host)
    {
        this.host = host; return this;
    }
    public ConnectionBuilder numConsumerThreads(int numConsumerThreads)
    {
        this.numConsumerThreads = numConsumerThreads; return this;
    }
    public ConnectionBuilder password(String password)
    {
        this.password = password; return this;
    }
    public ConnectionBuilder port(int port)
    {
        this.port = port; this.portSet = true; return this;
    }
    public ConnectionBuilder saslConfig(SaslConfig saslConfig)
    {
        this.saslConfig = saslConfig; return this;
    }
    public ConnectionBuilder socketFactory(SocketFactory socketFactory)
    {
        this.socketFactory = socketFactory;
        if (isSsl() && !this.portSet) this.port = DEFAULT_AMQP_OVER_SSL_PORT;
        return this;
    }
    public ConnectionBuilder ssl()
    {   return ssl(DEFAULT_SSL_PROTOCOL);
    }
    public ConnectionBuilder ssl(String protocol)
    {   return ssl(protocol, new NullTrustManager());
    }
    public ConnectionBuilder ssl(String protocol, TrustManager trustManager)
    {
        try {
            SSLContext context = SSLContext.getInstance(protocol);
            context.init(null, new TrustManager[] { trustManager }, null);
            return ssl(context);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
    public ConnectionBuilder ssl(SSLContext context)
    {
        if (context != null) socketFactory(context.getSocketFactory());
        return this;
    }
    public ConnectionBuilder username(String username)
    {
        this.username = username; return this;
    }
    public ConnectionBuilder virtualHost(String virtualHost)
    {
        this.virtualHost = virtualHost; return this;
    }
    public ConnectionBuilder uri(String uriString)
    {
        try { return uri(new URI(uriString)); }
        catch (URISyntaxException use) {
            throw new IllegalArgumentException(use);
        }
    }
    public ConnectionBuilder uri(URI uri)
    {
        //garner settings and check validity
        boolean setSsl = false;
        String lScheme = uri.getScheme().toLowerCase();
             if (lScheme.equals("amqp"))  { }
        else if (lScheme.equals("amqps")) { setSsl = true; }
        else {
            throw new IllegalArgumentException(
                    "Wrong scheme (" + uri.getScheme() + ") in AMQP URI: " + uri.toString()
                    );
        }

        String uHost = uri.getHost();
        int uPort = uri.getPort();
        boolean uSetPort = (uPort != -1);

        String uUser = null;
        String uPass = null;

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            String userAndPass[] = userInfo.split(":");
            if (userAndPass.length > 2 || userAndPass.length == 0) {
                throw new IllegalArgumentException(
                        "Bad user info (" + userInfo + ") in AMQP URI: " + uri.toString()
                        );
            }
            uUser = uriDecode(userAndPass[0]);
            if (userAndPass.length == 2) {
                uPass = uriDecode(userAndPass[1]);
            }
        }

        String uVHost = null;

        String path = uri.getRawPath();
        if (path != null && path.length() > 0) {
            if (path.indexOf('/', 1) != -1) {
                throw new IllegalArgumentException(
                        "Multiple segments in path (" + path + ") of AMQP URI: " + uri.toString()
                        );
            }
            uVHost = uriDecode(uri.getPath().substring(1));
        }

        // garnered without incident -- now set them
        if (setSsl) ssl();
        if (uHost != null) host(uHost);
        if (uSetPort) port(uPort);
        if (uUser != null) username(uUser);
        if (uPass != null) password(uPass);
        if (uVHost != null) virtualHost(uVHost);

        return this;
    }

    public Connection build() throws IOException
    {
        FrameHandler frameHandler = createFrameHandler();
        Map<String, Object> clientProperties = new HashMap<String, Object>(this.clientProperties);
        AMQConnection conn =
            new AMQConnection(this.username,
                              this.password,
                              frameHandler,
                              this.executor,
                              this.virtualHost,
                              clientProperties,
                              this.frameMax,
                              this.channelMax,
                              this.heartbeat,
                              this.saslConfig);
        conn.start();
        return conn;
    }

    public boolean isSsl()
    {
        return (this.socketFactory instanceof SSLSocketFactory);
    }
    private FrameHandler createFrameHandler() throws IOException
    {
        Socket socket = null;
        try {
            socket = this.socketFactory.createSocket();
            configureSocket(socket);
            socket.connect(new InetSocketAddress(this.host, this.port), this.connectionTimeout);
            return new SocketFrameHandler(socket);
        } catch (IOException ioe) {
            if (socket != null) try {socket.close();} catch (Exception e) {};
            throw ioe;
        }
    }

    private static String uriDecode(String s) {
        try {
            // URLDecode decodes '+' to a space, as for
            // form encoding.  So protect plus signs.
            return URLDecoder.decode(s.replace("+", "%2B"), "US-ASCII");
        }
        catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     *  A hook to insert custom configuration of the sockets
     *  used to connect to an AMQP server before they connect.
     *
     *  The default behaviour of this method is to disable Nagle's
     *  algorithm to get more consistently low latency.  However it
     *  may be overridden freely and there is no requirement to retain
     *  this behaviour.
     *
     *  @param socket The socket that is to be used for the Connection
     */
    protected void configureSocket(Socket socket) throws IOException
    {
        // disable Nagle's algorithm, for more consistently low latency
        socket.setTcpNoDelay(true);
    }
}
