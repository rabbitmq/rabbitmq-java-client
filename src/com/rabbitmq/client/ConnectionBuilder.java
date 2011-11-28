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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.FrameHandler;

/**
 * Builder class for {@link Connection}s.
 * <p/>
 * Builder attribute methods may be chained, for example:
 * <br/>
 * <pre>
 * Connection conn = <b>new</b> ConnectionBuilder()
 *                   .ssl().connectionTimeout(200).build();</pre>
 * To obtain the defaults, or the currently
 * configured attribute values of a builder,
 * call the <code>get</code>...<code>()</code> methods.
 * All of these getter methods return <i>copies</i> of the attributes,
 * <i>except</i> for {@link ConnectionBuilder#getSaslConfig() getSaslConfig()}
 * and {@link ConnectionBuilder#getSocketFactory() getSocketFactory()}.
 * <p/>
 * In particular,
 * {@link ConnectionBuilder#getClientProperties() getClientProperties}
 * will return a map which, if modified, will not affect the
 * {@link ConnectionBuilder}, nor any {@link Connection}s built from it.
 * <p/>
 * Similarly, {@link ConnectionBuilder#clientProperties(Map) clientProperties(Map)}
 * will also copy
 * the map. A simpler way to update client properties in-place, is to use
 * {@link ConnectionBuilder#clientProperty(String, Object) clientProperty(Key, Value)}.
 * Note that the {@link Object}s in the client properties map are never copied.
 * <p/>
 * <b>Concurrency</b><br/>
 * This class is <i>not</i> thread-safe.
 * Do not share objects of this class, nor its modifiable attributes, among threads.
 */
public final class ConnectionBuilder {

    private static final String DEFAULT_USER = "guest";
    private static final String DEFAULT_PASS = "guest";
    private static final String DEFAULT_VHOST = "/";
    private static final int    DEFAULT_CHANNEL_MAX = 0;
    private static final int    DEFAULT_FRAME_MAX = 0;
    private static final int    DEFAULT_HEARTBEAT = 0;
    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_AMQP_PORT = AMQP.PROTOCOL.PORT;
    private static final int    DEFAULT_AMQP_OVER_SSL_PORT = 5671;
    private static final int    DEFAULT_CONNECTION_TIMEOUT = 0;
    private static final String DEFAULT_SSL_PROTOCOL = "SSLv3";

    private static final ConnectionHelper DEFAULT_HELPER = new ConnectionHelper(){};

    private final ConnectionFrameHandler connectionFrameHandler;

    private int channelMax                       = DEFAULT_CHANNEL_MAX;
    private Map<String, Object> clientProperties = AMQConnection.defaultClientProperties();
    private int connectionTimeout                = DEFAULT_CONNECTION_TIMEOUT;
    private ExecutorService executor             = null;
    private int frameMax                         = DEFAULT_FRAME_MAX;
    private int heartbeat                        = DEFAULT_HEARTBEAT;
    private ConnectionHelper helper              = DEFAULT_HELPER;
    private String host                          = DEFAULT_HOST;
    private String password                      = DEFAULT_PASS;
    private int port                             = DEFAULT_AMQP_PORT;
    private boolean portSet                      = false; //indicates whether to use default
    private SaslConfig saslConfig                = DefaultSaslConfig.PLAIN;
    private SocketFactory socketFactory          = SocketFactory.getDefault();
    private String username                      = DEFAULT_USER;
    private String virtualHost                   = DEFAULT_VHOST;

    public ConnectionBuilder() {
        this(new ConnectionFrameHandler(){});
    }

    public ConnectionBuilder channelMax(int channelMax)
    {
        this.channelMax = channelMax; return this;
    }
    public ConnectionBuilder clientProperties(Map<String, Object> clientProperties)
    {
        this.clientProperties = (clientProperties == null)
                ? AMQConnection.defaultClientProperties()
                : new HashMap<String, Object>(clientProperties);
        return this;
    }
    public ConnectionBuilder clientProperty(String key, Object value)
    {
        if (key != null)
            if (value != null)
                this.clientProperties.remove(key);
            else
                this.clientProperties.put(key, value);
        return this;
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
    public ConnectionBuilder helper(ConnectionHelper helper)
    {
        this.helper = (helper == null) ? DEFAULT_HELPER : helper;
        return this;
    }
    public ConnectionBuilder host(String host)
    {
        this.host = (host == null) ? DEFAULT_HOST : host;
        return this;
    }
    public ConnectionBuilder password(String password)
    {
        this.password = (password == null) ? DEFAULT_PASS : password; return this;
    }
    public ConnectionBuilder port(int port)
    {
        this.port = port; this.portSet = true; return this;
    }
    public ConnectionBuilder saslConfig(SaslConfig saslConfig)
    {
        this.saslConfig = (saslConfig == null) ? DefaultSaslConfig.PLAIN : saslConfig;
        return this;
    }
    public ConnectionBuilder socketFactory(SocketFactory socketFactory)
    {
        this.socketFactory = (socketFactory == null) ? SocketFactory.getDefault() : socketFactory;
        if (isSsl() && !this.portSet) this.port = DEFAULT_AMQP_OVER_SSL_PORT;
        return this;
    }
    public ConnectionBuilder ssl(boolean sslOn) {
        return (sslOn ? ssl() : socketFactory(null));
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
        if (context == null)
            throw new IllegalArgumentException("Cannot use null SSLContext.");
        return socketFactory(context.getSocketFactory());
    }
    public ConnectionBuilder username(String username)
    {
        this.username = (username == null) ? DEFAULT_USER : username;
        return this;
    }
    public ConnectionBuilder virtualHost(String virtualHost)
    {
        this.virtualHost = (virtualHost == null) ? DEFAULT_VHOST : virtualHost;
        return this;
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
        boolean setSsl;
        String lScheme = uri.getScheme().toLowerCase();
             if (lScheme.equals("amqp"))  { setSsl = false;}
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
        ssl(setSsl);
        if (uHost != null) host(uHost);
        if (uSetPort) port(uPort);
        if (uUser != null) username(uUser);
        if (uPass != null) password(uPass);
        if (uVHost != null) virtualHost(uVHost);

        return this;
    }

    public Map<String, Object> getClientProperties()
    { return new HashMap<String, Object>(this.clientProperties); }
    public int getConnectionTimeout() { return this.connectionTimeout; }
    public String getHost() { return this.host; }
    public String getPassword() { return this.password; }
    public int getPort() { return this.port; }
    public int getChannelMax() { return this.channelMax; }
    public int getFrameMax() { return this.frameMax; }
    public int getHeartbeat() { return this.heartbeat; }
    public SaslConfig getSaslConfig() { return this.saslConfig; }
    public SocketFactory getSocketFactory() { return this.socketFactory; }
    public String getUsername() { return this.username; }
    public String getVirtualHost() { return this.virtualHost; }

    public Connection build()
        throws IOException
    {
        FrameHandler frameHandler = this.connectionFrameHandler.createFrameHandler(this, this.helper);
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

    ConnectionBuilder(ConnectionFrameHandler connectionFrameHandler) {
        this.connectionFrameHandler = connectionFrameHandler;
    }
}
