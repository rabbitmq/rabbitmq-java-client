// Copyright (c) 2007-2025 Broadcom. All Rights Reserved.
// The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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
package com.rabbitmq.client;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.client.impl.nio.SocketChannelFrameHandlerFactory;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.impl.recovery.RecoveredQueueNameSupplier;
import com.rabbitmq.client.impl.recovery.RetryHandler;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;
import com.rabbitmq.client.observation.ObservationCollector;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience factory class to facilitate opening a {@link Connection} to a RabbitMQ node.
 *
 * <p>Most connection and socket settings are configured using this factory. Some settings that
 * apply to connections can also be configured here and will apply to all connections produced by
 * this factory.
 */
public class ConnectionFactory implements Cloneable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFactory.class);

  private static final int MAX_UNSIGNED_SHORT = 65535;

  /** Default user name */
  public static final String DEFAULT_USER = "guest";

  /** Default password */
  public static final String DEFAULT_PASS = "guest";

  /** Default virtual host */
  public static final String DEFAULT_VHOST = "/";

  /**
   * Default maximum channel number; 2047 because it's 2048 on the server side minus channel 0,
   * which each connection uses for negotiation and error communication
   */
  public static final int DEFAULT_CHANNEL_MAX = 2047;

  /** Default maximum frame size; zero means no limit */
  public static final int DEFAULT_FRAME_MAX = 0;

  /** Default heart-beat interval; 60 seconds */
  public static final int DEFAULT_HEARTBEAT = 60;

  /** The default host */
  public static final String DEFAULT_HOST = "localhost";

  /** 'Use the default port' port */
  public static final int USE_DEFAULT_PORT = -1;

  /** The default non-ssl port */
  public static final int DEFAULT_AMQP_PORT = AMQP.PROTOCOL.PORT;

  /** The default ssl port */
  public static final int DEFAULT_AMQP_OVER_SSL_PORT = 5671;

  /** The default TCP connection timeout: 60 seconds */
  public static final int DEFAULT_CONNECTION_TIMEOUT = 60000;

  /**
   * The default AMQP 0-9-1 connection handshake timeout. See DEFAULT_CONNECTION_TIMEOUT for TCP
   * (socket) connection timeout.
   */
  public static final int DEFAULT_HANDSHAKE_TIMEOUT = 10000;

  /** The default shutdown timeout; zero means wait indefinitely */
  public static final int DEFAULT_SHUTDOWN_TIMEOUT = 10000;

  /** The default continuation timeout for RPC calls in channels: 10 minutes */
  public static final int DEFAULT_CHANNEL_RPC_TIMEOUT = (int) MINUTES.toMillis(10);

  /** The default network recovery interval: 5000 millis */
  public static final long DEFAULT_NETWORK_RECOVERY_INTERVAL = 5000;

  /** The default timeout for work pool enqueueing: no timeout */
  public static final int DEFAULT_WORK_POOL_TIMEOUT = -1;

  private static final String PREFERRED_TLS_PROTOCOL = "TLSv1.2";

  private static final String FALLBACK_TLS_PROTOCOL = "TLSv1";

  private String virtualHost = DEFAULT_VHOST;
  private String host = DEFAULT_HOST;
  private int port = USE_DEFAULT_PORT;
  private int requestedChannelMax = DEFAULT_CHANNEL_MAX;
  private int requestedFrameMax = DEFAULT_FRAME_MAX;
  private int requestedHeartbeat = DEFAULT_HEARTBEAT;
  private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
  private int handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;
  private int shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
  private Map<String, Object> _clientProperties = AMQConnection.defaultClientProperties();
  private SocketFactory socketFactory = null;
  private SaslConfig saslConfig = DefaultSaslConfig.PLAIN;

  private ExecutorService sharedExecutor;
  private ThreadFactory threadFactory = Executors.defaultThreadFactory();
  // minimises the number of threads rapid closure of many
  // connections uses, see rabbitmq/rabbitmq-java-client#86
  private ExecutorService shutdownExecutor;
  private ScheduledExecutorService heartbeatExecutor;
  private SocketConfigurator socketConf = SocketConfigurators.defaultConfigurator();
  private ExceptionHandler exceptionHandler = new DefaultExceptionHandler();
  private CredentialsProvider credentialsProvider =
      new DefaultCredentialsProvider(DEFAULT_USER, DEFAULT_PASS);

  private boolean automaticRecovery = true;
  private boolean topologyRecovery = true;
  private ExecutorService topologyRecoveryExecutor;

  // long is used to make sure the users can use both ints
  // and longs safely. It is unlikely that anybody'd need
  // to use recovery intervals > Integer.MAX_VALUE in practice.
  private long networkRecoveryInterval = DEFAULT_NETWORK_RECOVERY_INTERVAL;
  private RecoveryDelayHandler recoveryDelayHandler;

  private MetricsCollector metricsCollector;
  private ObservationCollector observationCollector = ObservationCollector.NO_OP;

  private boolean nio = false;
  private boolean netty = false;

  private FrameHandlerFactory frameHandlerFactory;
  private final NettyConfiguration nettyConf = new NettyConfiguration(this);
  private NioParams nioParams = new NioParams();

  private SslContextFactory sslContextFactory;

  /**
   * Continuation timeout on RPC calls.
   *
   * @since 4.1.0
   */
  private int channelRpcTimeout = DEFAULT_CHANNEL_RPC_TIMEOUT;

  /**
   * Whether or not channels check the reply type of an RPC call. Default is false.
   *
   * @since 4.2.0
   */
  private boolean channelShouldCheckRpcResponseType = false;

  /**
   * Listener called when a connection gets an IO error trying to write on the socket. Default
   * listener triggers connection recovery asynchronously and propagates the exception.
   *
   * @since 4.5.0
   */
  private ErrorOnWriteListener errorOnWriteListener;

  /**
   * Timeout in ms for work pool enqueuing.
   *
   * @since 4.5.0
   */
  private int workPoolTimeout = DEFAULT_WORK_POOL_TIMEOUT;

  /**
   * Filter to include/exclude entities from topology recovery.
   *
   * @since 4.8.0
   */
  private TopologyRecoveryFilter topologyRecoveryFilter;

  /**
   * Condition to trigger automatic connection recovery.
   *
   * @since 5.4.0
   */
  private Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition;

  /**
   * Retry handler for topology recovery. Default is no retry.
   *
   * @since 5.4.0
   */
  private RetryHandler topologyRecoveryRetryHandler;

  private RecoveredQueueNameSupplier recoveredQueueNameSupplier;

  /**
   * Traffic listener notified of inbound and outbound {@link Command}s.
   *
   * <p>Useful for debugging purposes. Default is no-op.
   *
   * @since 5.5.0
   */
  private TrafficListener trafficListener = TrafficListener.NO_OP;

  private CredentialsRefreshService credentialsRefreshService;

  /**
   * Maximum body size of inbound (received) messages in bytes.
   *
   * <p>Default value is 67,108,864 (64 MiB).
   */
  private int maxInboundMessageBodySize = 1_048_576 * 64;

  /**
   * @return the default host to use for connections
   */
  public String getHost() {
    return host;
  }

  /**
   * @param host the default host to use for connections
   */
  public void setHost(String host) {
    this.host = host;
  }

  public static int portOrDefault(int port, boolean ssl) {
    if (port != USE_DEFAULT_PORT) return port;
    else if (ssl) return DEFAULT_AMQP_OVER_SSL_PORT;
    else return DEFAULT_AMQP_PORT;
  }

  /**
   * @return the default port to use for connections
   */
  public int getPort() {
    return portOrDefault(port, isSSL());
  }

  /**
   * Set the target port.
   *
   * @param port the default port to use for connections
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * Retrieve the user name.
   *
   * @return the AMQP user name to use when connecting to the broker
   */
  public String getUsername() {
    return credentialsProvider.getUsername();
  }

  /**
   * Set the user name.
   *
   * @param username the AMQP user name to use when connecting to the broker
   */
  public void setUsername(String username) {
    this.credentialsProvider =
        new DefaultCredentialsProvider(username, this.credentialsProvider.getPassword());
  }

  /**
   * Retrieve the password.
   *
   * @return the password to use when connecting to the broker
   */
  public String getPassword() {
    return credentialsProvider.getPassword();
  }

  /**
   * Set the password.
   *
   * @param password the password to use when connecting to the broker
   */
  public void setPassword(String password) {
    this.credentialsProvider =
        new DefaultCredentialsProvider(this.credentialsProvider.getUsername(), password);
  }

  /**
   * Set a custom credentials provider. Default implementation uses static username and password.
   *
   * @param credentialsProvider The custom implementation of CredentialsProvider to use when
   *     connecting to the broker.
   * @see com.rabbitmq.client.impl.DefaultCredentialsProvider
   * @since 4.5.0
   */
  public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
  }

  /**
   * Retrieve the virtual host.
   *
   * @return the virtual host to use when connecting to the broker
   */
  public String getVirtualHost() {
    return this.virtualHost;
  }

  /**
   * Set the virtual host.
   *
   * @param virtualHost the virtual host to use when connecting to the broker
   */
  public void setVirtualHost(String virtualHost) {
    this.virtualHost = virtualHost;
  }

  /**
   * Convenience method for setting the fields in an AMQP URI: host, port, username, password and
   * virtual host. If any part of the URI is omitted, the ConnectionFactory's corresponding variable
   * is left unchanged.
   *
   * @param uri is the AMQP URI containing the data
   */
  public void setUri(URI uri)
      throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
    if ("amqp".equals(uri.getScheme().toLowerCase())) {
      setPort(DEFAULT_AMQP_PORT);
    } else if ("amqps".equals(uri.getScheme().toLowerCase())) {
      setPort(DEFAULT_AMQP_OVER_SSL_PORT);
      // SSL context factory not set yet, we use the default one
      if (this.sslContextFactory == null) {
        useSslProtocol();
      }
    } else {
      throw new IllegalArgumentException("Wrong scheme in AMQP URI: " + uri.getScheme());
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
        throw new IllegalArgumentException("Bad user info in AMQP " + "URI: " + userInfo);
      }

      setUsername(uriDecode(userPass[0]));
      if (userPass.length == 2) {
        setPassword(uriDecode(userPass[1]));
      }
    }

    String path = uri.getRawPath();
    if (path != null && path.length() > 0) {
      if (path.indexOf('/', 1) != -1) {
        throw new IllegalArgumentException("Multiple segments in " + "path of AMQP URI: " + path);
      }

      setVirtualHost(uriDecode(uri.getPath().substring(1)));
    }

    String rawQuery = uri.getRawQuery();
    if (rawQuery != null && rawQuery.length() > 0) {
      setQuery(rawQuery);
    }
  }

  /**
   * Convenience method for setting the fields in an AMQP URI: host, port, username, password and
   * virtual host. If any part of the URI is omitted, the ConnectionFactory's corresponding variable
   * is left unchanged. Note that not all valid AMQP URIs are accepted; in particular, the hostname
   * must be given if the port, username or password are given, and escapes in the hostname are not
   * permitted.
   *
   * @param uriString is the AMQP URI containing the data
   */
  public void setUri(String uriString)
      throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
    setUri(new URI(uriString));
  }

  private static String uriDecode(String s) {
    try {
      // URLDecode decodes '+' to a space, as for
      // form encoding.  So protect plus signs.
      return URLDecoder.decode(s.replace("+", "%2B"), "US-ASCII");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Map<String, BiConsumer<String, ConnectionFactory>>
      URI_QUERY_PARAMETER_HANDLERS =
          new HashMap<String, BiConsumer<String, ConnectionFactory>>() {
            {
              put(
                  "heartbeat",
                  (value, cf) -> {
                    try {
                      int heartbeatInt = Integer.parseInt(value);
                      cf.setRequestedHeartbeat(heartbeatInt);
                    } catch (NumberFormatException e) {
                      throw new IllegalArgumentException("Requested heartbeat must an integer");
                    }
                  });
              put(
                  "connection_timeout",
                  (value, cf) -> {
                    try {
                      int connectionTimeoutInt = Integer.parseInt(value);
                      cf.setConnectionTimeout(connectionTimeoutInt);
                    } catch (NumberFormatException e) {
                      throw new IllegalArgumentException("TCP connection timeout must an integer");
                    }
                  });
              put(
                  "channel_max",
                  (value, cf) -> {
                    try {
                      int channelMaxInt = Integer.parseInt(value);
                      cf.setRequestedChannelMax(channelMaxInt);
                    } catch (NumberFormatException e) {
                      throw new IllegalArgumentException("Requested channel max must an integer");
                    }
                  });
            }
          };

  /**
   * Convenience method for setting some fields from query parameters Will handle only a subset of
   * the query parameters supported by the official erlang client
   * https://www.rabbitmq.com/uri-query-parameters.html
   *
   * @param rawQuery is the string containing the raw query parameters part from a URI
   */
  private void setQuery(String rawQuery) {
    Map<String, String> parameters = new HashMap<>();
    // parsing the query parameters
    try {
      for (String param : rawQuery.split("&")) {
        String[] pair = param.split("=");
        String key = URLDecoder.decode(pair[0], "US-ASCII");
        String value = null;
        if (pair.length > 1) {
          value = URLDecoder.decode(pair[1], "US-ASCII");
        }
        parameters.put(key, value);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot parse the query parameters", e);
    }

    for (Entry<String, String> entry : parameters.entrySet()) {
      BiConsumer<String, ConnectionFactory> handler =
          URI_QUERY_PARAMETER_HANDLERS.get(entry.getKey());
      if (handler != null) {
        handler.accept(entry.getValue(), this);
      } else {
        processUriQueryParameter(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Hook to process query parameters not handled natively. Handled natively: <code>heartbeat</code>
   * , <code>connection_timeout</code>, <code>channel_max</code>.
   *
   * @param key
   * @param value
   */
  protected void processUriQueryParameter(String key, String value) {}

  /**
   * Retrieve the requested maximum channel number
   *
   * @return the initially requested maximum channel number; zero for unlimited
   */
  public int getRequestedChannelMax() {
    return this.requestedChannelMax;
  }

  /**
   * Set the requested maximum channel number.
   *
   * <p>Note the value must be between 0 and 65535 (unsigned short in AMQP 0-9-1).
   *
   * @param requestedChannelMax initially requested maximum channel number; zero for unlimited
   */
  public void setRequestedChannelMax(int requestedChannelMax) {
    this.requestedChannelMax = ensureUnsignedShort(requestedChannelMax);
    if (this.requestedChannelMax != requestedChannelMax) {
      LOGGER.warn(
          "Requested channel max must be between 0 and {}, value has been set to {} instead of {}",
          MAX_UNSIGNED_SHORT,
          this.requestedChannelMax,
          requestedChannelMax);
    }
  }

  /**
   * Retrieve the requested maximum frame size
   *
   * @return the initially requested maximum frame size, in octets; zero for unlimited
   */
  public int getRequestedFrameMax() {
    return this.requestedFrameMax;
  }

  /**
   * Set the requested maximum frame size
   *
   * @param requestedFrameMax initially requested maximum frame size, in octets; zero for unlimited
   */
  public void setRequestedFrameMax(int requestedFrameMax) {
    this.requestedFrameMax = requestedFrameMax;
  }

  /**
   * Retrieve the requested heartbeat interval.
   *
   * @return the initially requested heartbeat interval, in seconds; zero for none
   */
  public int getRequestedHeartbeat() {
    return this.requestedHeartbeat;
  }

  /**
   * Set the TCP connection timeout.
   *
   * @param timeout connection TCP establishment timeout in milliseconds; zero for infinite
   */
  public void setConnectionTimeout(int timeout) {
    if (timeout < 0) {
      throw new IllegalArgumentException("TCP connection timeout cannot be negative");
    }
    this.connectionTimeout = timeout;
  }

  /**
   * Retrieve the TCP connection timeout.
   *
   * @return the TCP connection timeout, in milliseconds; zero for infinite
   */
  public int getConnectionTimeout() {
    return this.connectionTimeout;
  }

  /**
   * Retrieve the AMQP 0-9-1 protocol handshake timeout.
   *
   * @return the AMQP0-9-1 protocol handshake timeout, in milliseconds
   */
  public int getHandshakeTimeout() {
    return handshakeTimeout;
  }

  /**
   * Set the AMQP0-9-1 protocol handshake timeout.
   *
   * @param timeout the AMQP0-9-1 protocol handshake timeout, in milliseconds
   */
  public void setHandshakeTimeout(int timeout) {
    if (timeout < 0) {
      throw new IllegalArgumentException("handshake timeout cannot be negative");
    }
    this.handshakeTimeout = timeout;
  }

  /**
   * Set the shutdown timeout. This is the amount of time that Consumer implementations have to
   * continue working through deliveries (and other Consumer callbacks) <b>after</b> the connection
   * has closed but before the ConsumerWorkService is torn down. If consumers exceed this timeout
   * then any remaining queued deliveries (and other Consumer callbacks, <b>including</b> the
   * Consumer's handleShutdownSignal() invocation) will be lost.
   *
   * @param shutdownTimeout shutdown timeout in milliseconds; zero for infinite; default 10000
   */
  public void setShutdownTimeout(int shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
  }

  /**
   * Retrieve the shutdown timeout.
   *
   * @return the shutdown timeout, in milliseconds; zero for infinite
   */
  public int getShutdownTimeout() {
    return shutdownTimeout;
  }

  /**
   * Set the requested heartbeat timeout. Heartbeat frames will be sent at about 1/2 the timeout
   * interval. If server heartbeat timeout is configured to a non-zero value, this method can only
   * be used to lower the value; otherwise any value provided by the client will be used.
   *
   * <p>Note the value must be between 0 and 65535 (unsigned short in AMQP 0-9-1).
   *
   * @param requestedHeartbeat the initially requested heartbeat timeout, in seconds; zero for none
   * @see <a href="https://rabbitmq.com/heartbeats.html">RabbitMQ Heartbeats Guide</a>
   */
  public void setRequestedHeartbeat(int requestedHeartbeat) {
    this.requestedHeartbeat = ensureUnsignedShort(requestedHeartbeat);
    if (this.requestedHeartbeat != requestedHeartbeat) {
      LOGGER.warn(
          "Requested heartbeat must be between 0 and {}, value has been set to {} instead of {}",
          MAX_UNSIGNED_SHORT,
          this.requestedHeartbeat,
          requestedHeartbeat);
    }
  }

  /**
   * Retrieve the currently-configured table of client properties that will be sent to the server
   * during connection startup. Clients may add, delete, and alter keys in this table. Such changes
   * will take effect when the next new connection is started using this factory.
   *
   * @return the map of client properties
   * @see #setClientProperties
   */
  public Map<String, Object> getClientProperties() {
    return _clientProperties;
  }

  /**
   * Replace the table of client properties that will be sent to the server during subsequent
   * connection startups.
   *
   * @param clientProperties the map of extra client properties
   * @see #getClientProperties
   */
  public void setClientProperties(Map<String, Object> clientProperties) {
    _clientProperties = clientProperties;
  }

  /**
   * Gets the sasl config to use when authenticating
   *
   * @return the sasl config
   * @see com.rabbitmq.client.SaslConfig
   */
  public SaslConfig getSaslConfig() {
    return saslConfig;
  }

  /**
   * Sets the sasl config to use when authenticating
   *
   * @param saslConfig
   * @see com.rabbitmq.client.SaslConfig
   */
  public void setSaslConfig(SaslConfig saslConfig) {
    this.saslConfig = saslConfig;
  }

  /** Retrieve the socket factory used to make connections with. */
  public SocketFactory getSocketFactory() {
    return this.socketFactory;
  }

  /**
   * Set the socket factory used to create sockets for new connections. Can be used to customize
   * TLS-related settings by passing in a javax.net.ssl.SSLSocketFactory instance. Note this applies
   * only to blocking IO, not to NIO, as the NIO API doesn't use the SocketFactory API.
   *
   * @see #useSslProtocol
   */
  public void setSocketFactory(SocketFactory factory) {
    this.socketFactory = factory;
  }

  /**
   * Get the socket configurator.
   *
   * @see #setSocketConfigurator(SocketConfigurator)
   */
  public SocketConfigurator getSocketConfigurator() {
    return socketConf;
  }

  /**
   * Set the socket configurator. This gets a chance to "configure" a socket before it has been
   * opened. The default socket configurator disables Nagle's algorithm.
   *
   * @param socketConfigurator the configurator to use
   */
  public void setSocketConfigurator(SocketConfigurator socketConfigurator) {
    this.socketConf = socketConfigurator;
  }

  /**
   * Set the executor to use for consumer operation dispatch by default for newly created
   * connections. All connections that use this executor share it.
   *
   * <p>It's developer's responsibility to shut down the executor when it is no longer needed.
   *
   * @param executor executor service to be used for consumer operation
   */
  public void setSharedExecutor(ExecutorService executor) {
    this.sharedExecutor = executor;
  }

  /**
   * Set the executor to use for connection shutdown. All connections that use this executor share
   * it.
   *
   * <p>It's developer's responsibility to shut down the executor when it is no longer needed.
   *
   * @param executor executor service to be used for connection shutdown
   */
  public void setShutdownExecutor(ExecutorService executor) {
    this.shutdownExecutor = executor;
  }

  /**
   * Set the executor to use to send heartbeat frames. All connections that use this executor share
   * it.
   *
   * <p>It's developer's responsibility to shut down the executor when it is no longer needed.
   *
   * <p>Note there is no need to set a heartbeat executor when using Netty.
   *
   * @param executor executor service to be used to send heartbeat
   */
  public void setHeartbeatExecutor(ScheduledExecutorService executor) {
    this.heartbeatExecutor = executor;
  }

  /**
   * Retrieve the thread factory used to instantiate new threads.
   *
   * @see ThreadFactory
   */
  public ThreadFactory getThreadFactory() {
    return threadFactory;
  }

  /**
   * Set the thread factory used to instantiate new threads.
   *
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
   *
   * @see com.rabbitmq.client.ExceptionHandler
   */
  public void setExceptionHandler(ExceptionHandler exceptionHandler) {
    if (exceptionHandler == null) {
      throw new IllegalArgumentException("exception handler cannot be null!");
    }
    this.exceptionHandler = exceptionHandler;
  }

  public boolean isSSL() {
    return getSocketFactory() instanceof SSLSocketFactory
        || sslContextFactory != null
        || this.nettyConf.isTls();
  }

  /**
   * Convenience method for configuring TLS using the default set of TLS protocols and a trusting
   * TrustManager. This setup is <strong>only suitable for development and QA environments</strong>.
   * The trust manager will <strong>trust every server certificate presented</strong> to it, this is
   * convenient for local development but <strong>not recommended to use in production</strong> as
   * it provides no protection against man-in-the-middle attacks. Prefer {@link
   * #useSslProtocol(SSLContext)}.
   *
   * <p>Note this method has NO effect when using Netty, use {@link
   * com.rabbitmq.client.ConnectionFactory.NettyConfiguration#sslContext(io.netty.handler.ssl.SslContext)}
   * instead.
   */
  public void useSslProtocol() throws NoSuchAlgorithmException, KeyManagementException {
    useSslProtocol(
        computeDefaultTlsProtocol(
            SSLContext.getDefault().getSupportedSSLParameters().getProtocols()));
  }

  /**
   * Convenience method for configuring TLS using the supplied protocol and a very trusting
   * TrustManager. This setup is <strong>only suitable for development and QA environments</strong>.
   * The trust manager <strong>will trust every server certificate presented</strong> to it, this is
   * convenient for local development but not recommended to use in production as it
   * <strong>provides no protection against man-in-the-middle attacks</strong>.
   *
   * <p>Use {@link #useSslProtocol(SSLContext)} in production environments. The produced {@link
   * SSLContext} instance will be shared by all the connections created by this connection factory.
   *
   * <p>Use {@link #setSslContextFactory(SslContextFactory)} for more flexibility.
   *
   * <p>Note this method has NO effect when using Netty, use {@link
   * com.rabbitmq.client.ConnectionFactory.NettyConfiguration#sslContext(io.netty.handler.ssl.SslContext)}
   * instead.
   *
   * @see #setSslContextFactory(SslContextFactory)
   */
  public void useSslProtocol(String protocol)
      throws NoSuchAlgorithmException, KeyManagementException {
    useSslProtocol(protocol, new TrustEverythingTrustManager());
  }

  /**
   * Convenience method for configuring TLS. Pass in the TLS protocol version to use, e.g. "TLSv1.2"
   * or "TLSv1.1", and a desired {@link TrustManager}.
   *
   * <p>The produced {@link SSLContext} instance will be shared with all the connections created by
   * this connection factory. Use {@link #setSslContextFactory(SslContextFactory)} for more
   * flexibility.
   *
   * <p>Note this method has NO effect when using Netty, use {@link
   * com.rabbitmq.client.ConnectionFactory.NettyConfiguration#sslContext(io.netty.handler.ssl.SslContext)}
   * instead.
   *
   * @param protocol the TLS protocol to use.
   * @param trustManager the {@link TrustManager} implementation to use.
   * @see #setSslContextFactory(SslContextFactory)
   * @see #useSslProtocol(SSLContext)
   */
  public void useSslProtocol(String protocol, TrustManager trustManager)
      throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext c = SSLContext.getInstance(protocol);
    c.init(null, new TrustManager[] {trustManager}, null);
    useSslProtocol(c);
  }

  /**
   * Sets up TLS with an initialized {@link SSLContext}. The caller is responsible for setting up
   * the context with a {@link TrustManager} with suitable security guarantees, e.g. peer
   * verification.
   *
   * <p>The {@link SSLContext} instance will be shared with all the connections created by this
   * connection factory. Use {@link #setSslContextFactory(SslContextFactory)} for more flexibility.
   *
   * <p>Note this method has NO effect when using Netty, use {@link
   * com.rabbitmq.client.ConnectionFactory.NettyConfiguration#sslContext(io.netty.handler.ssl.SslContext)}
   * instead.
   *
   * @param context An initialized SSLContext
   * @see #setSslContextFactory(SslContextFactory)
   */
  public void useSslProtocol(SSLContext context) {
    this.sslContextFactory = name -> context;
    setSocketFactory(context.getSocketFactory());
  }

  /**
   * Enable server hostname verification for TLS connections.
   *
   * <p>This enables hostname verification regardless of the IO mode used (blocking or non-blocking
   * IO).
   *
   * <p>This can be called typically after setting the {@link SSLContext} with one of the <code>
   * useSslProtocol</code> methods.
   *
   * <p>Note this method has NO effect when using Netty, use {@link
   * com.rabbitmq.client.ConnectionFactory.NettyConfiguration#sslContext(io.netty.handler.ssl.SslContext)}
   * instead.
   *
   * @see NioParams#enableHostnameVerification()
   * @see NioParams#setSslEngineConfigurator(SslEngineConfigurator)
   * @see SslEngineConfigurators#ENABLE_HOSTNAME_VERIFICATION
   * @see SocketConfigurators#ENABLE_HOSTNAME_VERIFICATION
   * @see ConnectionFactory#useSslProtocol(String)
   * @see ConnectionFactory#useSslProtocol(SSLContext)
   * @see ConnectionFactory#useSslProtocol()
   * @see ConnectionFactory#useSslProtocol(String, TrustManager)
   * @since 5.4.0
   */
  public void enableHostnameVerification() {
    enableHostnameVerificationForNio();
    enableHostnameVerificationForBlockingIo();
  }

  protected void enableHostnameVerificationForNio() {
    if (this.nioParams == null) {
      this.nioParams = new NioParams();
    }
    this.nioParams = this.nioParams.enableHostnameVerification();
  }

  protected void enableHostnameVerificationForBlockingIo() {
    if (this.socketConf == null) {
      this.socketConf =
          SocketConfigurators.builder().defaultConfigurator().enableHostnameVerification().build();
    } else {
      this.socketConf = this.socketConf.andThen(SocketConfigurators.enableHostnameVerification());
    }
  }

  public static String computeDefaultTlsProtocol(String[] supportedProtocols) {
    if (supportedProtocols != null) {
      for (String supportedProtocol : supportedProtocols) {
        if (PREFERRED_TLS_PROTOCOL.equalsIgnoreCase(supportedProtocol)) {
          return supportedProtocol;
        }
      }
    }
    return FALLBACK_TLS_PROTOCOL;
  }

  /**
   * Returns true if <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, false otherwise
   *
   * @return true if automatic connection recovery is enabled, false otherwise
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public boolean isAutomaticRecoveryEnabled() {
    return automaticRecovery;
  }

  /**
   * Enables or disables <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic
   * connection recovery</a>.
   *
   * @param automaticRecovery if true, enables connection recovery
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public void setAutomaticRecoveryEnabled(boolean automaticRecovery) {
    this.automaticRecovery = automaticRecovery;
  }

  /**
   * Returns true if topology recovery is enabled, false otherwise
   *
   * @return true if topology recovery is enabled, false otherwise
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public boolean isTopologyRecoveryEnabled() {
    return topologyRecovery;
  }

  /**
   * Enables or disables topology recovery
   *
   * @param topologyRecovery if true, enables topology recovery
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public void setTopologyRecoveryEnabled(boolean topologyRecovery) {
    this.topologyRecovery = topologyRecovery;
  }

  /**
   * Get the executor to use for parallel topology recovery. If null (the default), recovery is done
   * single threaded on the main connection thread.
   *
   * @return thread pool executor
   * @since 4.7.0
   */
  public ExecutorService getTopologyRecoveryExecutor() {
    return topologyRecoveryExecutor;
  }

  /**
   * Set the executor to use for parallel topology recovery. If null (the default), recovery is done
   * single threaded on the main connection thread. It is recommended to pass a ThreadPoolExecutor
   * that will allow its core threads to timeout so these threads can die when recovery is complete.
   * It's developer's responsibility to shut down the executor when it is no longer needed. Note:
   * your {@link ExceptionHandler#handleTopologyRecoveryException(Connection, Channel,
   * TopologyRecoveryException)} method should be thread-safe.
   *
   * @param topologyRecoveryExecutor thread pool executor
   * @since 4.7.0
   */
  public void setTopologyRecoveryExecutor(final ExecutorService topologyRecoveryExecutor) {
    this.topologyRecoveryExecutor = topologyRecoveryExecutor;
  }

  public void setMetricsCollector(MetricsCollector metricsCollector) {
    this.metricsCollector = metricsCollector;
  }

  public MetricsCollector getMetricsCollector() {
    return metricsCollector;
  }

  /**
   * Set observation collector.
   *
   * @param observationCollector the collector instance
   * @since 5.19.0
   * @see ObservationCollector
   * @see com.rabbitmq.client.observation.micrometer.MicrometerObservationCollectorBuilder
   */
  public void setObservationCollector(ObservationCollector observationCollector) {
    this.observationCollector = observationCollector;
  }

  /**
   * Set a {@link CredentialsRefreshService} instance to handle credentials refresh if appropriate.
   *
   * <p>Each created connection will register to the refresh service to send an AMQP <code>
   * update.secret</code> frame when credentials are about to expire. This is the refresh service
   * responsibility to schedule credentials refresh and <code>udpate.secret</code> frame sending,
   * based on the information provided by the {@link CredentialsProvider}.
   *
   * <p>Note the {@link CredentialsRefreshService} is used only when the {@link CredentialsProvider}
   * signals credentials can expire, by returning a non-null value from {@link
   * CredentialsProvider#getTimeBeforeExpiration()}.
   *
   * @param credentialsRefreshService the refresh service to use
   * @see #setCredentialsProvider(CredentialsProvider)
   * @see DefaultCredentialsRefreshService
   */
  public void setCredentialsRefreshService(CredentialsRefreshService credentialsRefreshService) {
    this.credentialsRefreshService = credentialsRefreshService;
  }

  protected synchronized FrameHandlerFactory createFrameHandlerFactory() throws IOException {
    if (nio) {
      if (this.frameHandlerFactory == null) {
        if (this.nioParams.getNioExecutor() == null && this.nioParams.getThreadFactory() == null) {
          this.nioParams.setThreadFactory(getThreadFactory());
        }
        this.frameHandlerFactory =
            new SocketChannelFrameHandlerFactory(
                connectionTimeout,
                nioParams,
                isSSL(),
                sslContextFactory,
                this.maxInboundMessageBodySize);
      }
      return this.frameHandlerFactory;
    } else if (netty) {
      if (this.frameHandlerFactory == null) {
        this.frameHandlerFactory =
            new NettyFrameHandlerFactory(
                this.nettyConf.eventLoopGroup,
                this.nettyConf.channelCustomizer,
                this.nettyConf.bootstrapCustomizer,
                this.nettyConf.sslContextFactory,
                this.nettyConf.enqueuingTimeout,
                connectionTimeout,
                socketConf,
                maxInboundMessageBodySize);
      }
      return this.frameHandlerFactory;
    } else {
      return new SocketFrameHandlerFactory(
          connectionTimeout,
          socketFactory,
          socketConf,
          isSSL(),
          this.shutdownExecutor,
          sslContextFactory,
          this.maxInboundMessageBodySize);
    }
  }

  /**
   * Create a new broker connection, picking the first available address from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(Address[] addrs) throws IOException, TimeoutException {
    return newConnection(this.sharedExecutor, Arrays.asList(addrs), null);
  }

  /**
   * Create a new broker connection, picking the first available address from the list provided by
   * the {@link AddressResolver}.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address provided by the {@link
   * AddressResolver}.
   *
   * @param addressResolver discovery service to list potential addresses (hostname/port pairs) to
   *     connect to
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(AddressResolver addressResolver)
      throws IOException, TimeoutException {
    return newConnection(this.sharedExecutor, addressResolver, null);
  }

  /**
   * Create a new broker connection with a client-provided name, picking the first available address
   * from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
   * @param clientProvidedName application-specific connection name, will be displayed in the
   *     management UI if RabbitMQ server supports it. This value doesn't have to be unique and
   *     cannot be used as a connection identifier e.g. in HTTP API requests. This value is supposed
   *     to be human-readable.
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(Address[] addrs, String clientProvidedName)
      throws IOException, TimeoutException {
    return newConnection(this.sharedExecutor, Arrays.asList(addrs), clientProvidedName);
  }

  /**
   * Create a new broker connection, picking the first available address from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param addrs a List of known broker addresses (hostname/port pairs) to try in order
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(List<Address> addrs) throws IOException, TimeoutException {
    return newConnection(this.sharedExecutor, addrs, null);
  }

  /**
   * Create a new broker connection with a client-provided name, picking the first available address
   * from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param addrs a List of known broker addresses (hostname/port pairs) to try in order
   * @param clientProvidedName application-specific connection name, will be displayed in the
   *     management UI if RabbitMQ server supports it. This value doesn't have to be unique and
   *     cannot be used as a connection identifier e.g. in HTTP API requests. This value is supposed
   *     to be human-readable.
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(List<Address> addrs, String clientProvidedName)
      throws IOException, TimeoutException {
    return newConnection(this.sharedExecutor, addrs, clientProvidedName);
  }

  /**
   * Create a new broker connection, picking the first available address from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param executor thread execution service for consumers on the connection
   * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
   * @return an interface to the connection
   * @throws java.io.IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(ExecutorService executor, Address[] addrs)
      throws IOException, TimeoutException {
    return newConnection(executor, Arrays.asList(addrs), null);
  }

  /**
   * Create a new broker connection with a client-provided name, picking the first available address
   * from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param executor thread execution service for consumers on the connection
   * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
   * @param clientProvidedName application-specific connection name, will be displayed in the
   *     management UI if RabbitMQ server supports it. This value doesn't have to be unique and
   *     cannot be used as a connection identifier e.g. in HTTP API requests. This value is supposed
   *     to be human-readable.
   * @return an interface to the connection
   * @throws java.io.IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(
      ExecutorService executor, Address[] addrs, String clientProvidedName)
      throws IOException, TimeoutException {
    return newConnection(executor, Arrays.asList(addrs), clientProvidedName);
  }

  /**
   * Create a new broker connection, picking the first available address from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param executor thread execution service for consumers on the connection
   * @param addrs a List of known broker addrs (hostname/port pairs) to try in order
   * @return an interface to the connection
   * @throws java.io.IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(ExecutorService executor, List<Address> addrs)
      throws IOException, TimeoutException {
    return newConnection(executor, addrs, null);
  }

  /**
   * Create a new broker connection, picking the first available address from the list provided by
   * the {@link AddressResolver}.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address provided by the {@link
   * AddressResolver}.
   *
   * @param executor thread execution service for consumers on the connection
   * @param addressResolver discovery service to list potential addresses (hostname/port pairs) to
   *     connect to
   * @return an interface to the connection
   * @throws java.io.IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(ExecutorService executor, AddressResolver addressResolver)
      throws IOException, TimeoutException {
    return newConnection(executor, addressResolver, null);
  }

  /**
   * Create a new broker connection with a client-provided name, picking the first available address
   * from the list.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address from the provided list.
   *
   * @param executor thread execution service for consumers on the connection
   * @param addrs a List of known broker addrs (hostname/port pairs) to try in order
   * @param clientProvidedName application-specific connection name, will be displayed in the
   *     management UI if RabbitMQ server supports it. This value doesn't have to be unique and
   *     cannot be used as a connection identifier e.g. in HTTP API requests. This value is supposed
   *     to be human-readable.
   * @return an interface to the connection
   * @throws java.io.IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(
      ExecutorService executor, List<Address> addrs, String clientProvidedName)
      throws IOException, TimeoutException {
    return newConnection(executor, createAddressResolver(addrs), clientProvidedName);
  }

  /**
   * Create a new broker connection with a client-provided name, picking the first available address
   * from the list provided by the {@link AddressResolver}.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Future reconnection attempts will pick a random accessible address provided by the {@link
   * AddressResolver}.
   *
   * @param executor thread execution service for consumers on the connection
   * @param addressResolver discovery service to list potential addresses (hostname/port pairs) to
   *     connect to
   * @param clientProvidedName application-specific connection name, will be displayed in the
   *     management UI if RabbitMQ server supports it. This value doesn't have to be unique and
   *     cannot be used as a connection identifier e.g. in HTTP API requests. This value is supposed
   *     to be human-readable.
   * @return an interface to the connection
   * @throws java.io.IOException if it encounters a problem
   * @see <a href="https://www.rabbitmq.com/api-guide.html#recovery">Automatic Recovery</a>
   */
  public Connection newConnection(
      ExecutorService executor, AddressResolver addressResolver, String clientProvidedName)
      throws IOException, TimeoutException {
    if (this.metricsCollector == null) {
      this.metricsCollector = new NoOpMetricsCollector();
    }
    // make sure we respect the provided thread factory
    FrameHandlerFactory fhFactory = createFrameHandlerFactory();
    ConnectionParams params = params(executor);
    // set client-provided via a client property
    if (clientProvidedName != null) {
      Map<String, Object> properties = new HashMap<String, Object>(params.getClientProperties());
      properties.put("connection_name", clientProvidedName);
      params.setClientProperties(properties);
    }

    if (isAutomaticRecoveryEnabled()) {
      // see com.rabbitmq.client.impl.recovery.RecoveryAwareAMQConnectionFactory#newConnection
      // No Sonar: no need to close this resource because we're the one that creates it
      // and hands it over to the user
      AutorecoveringConnection conn =
          new AutorecoveringConnection(
              params,
              fhFactory,
              addressResolver,
              metricsCollector,
              observationCollector); // NOSONAR

      conn.init();
      return conn;
    } else {
      List<Address> addrs = addressResolver.getAddresses();
      Exception lastException = null;
      for (Address addr : addrs) {
        try {
          FrameHandler handler = fhFactory.create(addr, clientProvidedName);
          AMQConnection conn = createConnection(params, handler, metricsCollector);
          conn.start();
          this.metricsCollector.newConnection(conn);
          return conn;
        } catch (IOException e) {
          lastException = e;
        } catch (TimeoutException te) {
          lastException = te;
        }
      }
      if (lastException != null) {
        if (lastException instanceof IOException) {
          throw (IOException) lastException;
        } else if (lastException instanceof TimeoutException) {
          throw (TimeoutException) lastException;
        }
      }
      throw new IOException("failed to connect");
    }
  }

  /**
   * Sets the parameters when using NIO.
   *
   * @param nioParams
   * @see NioParams
   * @deprecated user {@link #netty()} instead
   */
  @Deprecated
  public ConnectionFactory setNioParams(NioParams nioParams) {
    this.nioParams = nioParams;
    return this;
  }

  /**
   * Retrieve the parameters for NIO mode.
   *
   * @return
   * @deprecated Use {@link #netty()}
   */
  @Deprecated
  public NioParams getNioParams() {
    return nioParams;
  }

  /**
   * Use non-blocking IO (NIO) for communication with the server. With NIO, several connections
   * created from the same {@link ConnectionFactory} can use the same IO thread.
   *
   * <p>A client process using a lot of not-so-active connections can benefit from NIO, as it would
   * use fewer threads than with the traditional, blocking IO mode.
   *
   * <p>Use {@link NioParams} to tune NIO and a {@link SocketChannelConfigurator} to configure the
   * underlying {@link java.nio.channels.SocketChannel}s for connections.
   *
   * @see NioParams
   * @see SocketChannelConfigurator
   * @see java.nio.channels.SocketChannel
   * @see java.nio.channels.Selector
   * @deprecated Use {@link #netty()} instead
   */
  @Deprecated
  public ConnectionFactory useNio() {
    this.nio = true;
    this.netty = false;
    return this;
  }

  /**
   * Use blocking IO for communication with the server. With blocking IO, each connection creates
   * its own thread to read data from the server.
   */
  public ConnectionFactory useBlockingIo() {
    this.nio = false;
    this.netty = false;
    return this;
  }

  /**
   * Maximum body size of inbound (received) messages in bytes.
   *
   * <p>Default value is 67,108,864 (64 MiB).
   *
   * @param maxInboundMessageBodySize the maximum size of inbound messages
   */
  public void setMaxInboundMessageBodySize(int maxInboundMessageBodySize) {
    if (maxInboundMessageBodySize <= 0) {
      throw new IllegalArgumentException(
          "Max inbound message body size must be greater than 0: " + maxInboundMessageBodySize);
    }
    this.maxInboundMessageBodySize = maxInboundMessageBodySize;
  }

  private ConnectionFactory useNetty() {
    this.netty = true;
    this.nio = false;
    return this;
  }

  /**
   * Activate and configure Netty for the IO layer.
   *
   * @return Netty configuration
   * @since 5.27.0
   */
  public NettyConfiguration netty() {
    useNetty();
    return this.nettyConf;
  }

  /**
   * Helper class to configure Netty.
   *
   * @since 5.27.0
   */
  public static final class NettyConfiguration {

    private final ConnectionFactory cf;
    private EventLoopGroup eventLoopGroup;
    private Consumer<io.netty.channel.Channel> channelCustomizer = ch -> {};
    private Consumer<Bootstrap> bootstrapCustomizer = b -> {};
    private Function<String, SslContext> sslContextFactory;
    private Duration enqueuingTimeout = Duration.ofSeconds(10);

    private NettyConfiguration(ConnectionFactory cf) {
      this.cf = cf;
    }

    /**
     * Set the {@link io.netty.channel.EventLoopGroup} shared by all the connections created with
     * this connection factory.
     *
     * <p>A connection uses a one-thread NIO event loop group if none is provided. It disposes it
     * when it closes itself.
     *
     * <p>It is recommended to set an event loop group with an appropriate number of threads if
     * connection factory is meant to create a significant number of connections.
     *
     * <p>It is the developer's responsibility to close the event loop group they provide.
     *
     * @param eventLoopGroup the event loop group to use
     * @return this configuration instance
     */
    public NettyConfiguration eventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
      return this;
    }

    /**
     * An extension point to customize Netty's {@link io.netty.channel.Channel}s used for
     * connections.
     *
     * @param channelCustomizer the customization callback
     * @return this configuration instance
     */
    public NettyConfiguration channelCustomizer(
        Consumer<io.netty.channel.Channel> channelCustomizer) {
      this.channelCustomizer = channelCustomizer;
      return this;
    }

    /**
     * An extension point to customize Netty's {@link Bootstrap}s used to configure connections.
     *
     * @param bootstrapCustomizer the bootstrap customization callback
     * @return this configuration instance
     */
    public NettyConfiguration bootstrapCustomizer(Consumer<Bootstrap> bootstrapCustomizer) {
      this.bootstrapCustomizer = bootstrapCustomizer;
      return this;
    }

    /**
     * Netty {@link SslContext} for TLS connections.
     *
     * <p>Use {@link SslContextBuilder#forClient()} to configure and create an instance.
     *
     * @param sslContext the SSL context
     * @return this configuration instance
     */
    public NettyConfiguration sslContext(SslContext sslContext) {
      this.sslContextFactory = name -> sslContext;
      return this;
    }

    /**
     * A factory to create {@link io.netty.handler.ssl.SslContext} depending on the connection name.
     *
     * @param sslContextFactory the factory
     * @return this configuration instance
     */
    public NettyConfiguration sslContextFactory(Function<String, SslContext> sslContextFactory) {
      this.sslContextFactory = sslContextFactory;
      return this;
    }

    /**
     * Set the timeout to enqueue outbound frames.
     *
     * <p>Default is 10 seconds.
     *
     * @param enqueuingTimeout the enqueuing timeout
     * @return this configuration instance
     */
    public NettyConfiguration enqueuingTimeout(Duration enqueuingTimeout) {
      this.enqueuingTimeout = enqueuingTimeout;
      return this;
    }

    /**
     * Go back to the connection factory.
     *
     * @return the connection factory
     */
    public ConnectionFactory connectionFactory() {
      return this.cf;
    }

    private boolean isTls() {
      return this.sslContextFactory != null;
    }
  }

  public ConnectionParams params(ExecutorService consumerWorkServiceExecutor) {
    ConnectionParams result = new ConnectionParams();

    result.setCredentialsProvider(credentialsProvider);
    result.setConsumerWorkServiceExecutor(consumerWorkServiceExecutor);
    result.setVirtualHost(virtualHost);
    result.setClientProperties(getClientProperties());
    result.setRequestedFrameMax(requestedFrameMax);
    result.setRequestedChannelMax(requestedChannelMax);
    result.setShutdownTimeout(shutdownTimeout);
    result.setSaslConfig(saslConfig);
    result.setNetworkRecoveryInterval(networkRecoveryInterval);
    result.setRecoveryDelayHandler(recoveryDelayHandler);
    result.setTopologyRecovery(topologyRecovery);
    result.setTopologyRecoveryExecutor(topologyRecoveryExecutor);
    result.setExceptionHandler(exceptionHandler);
    result.setThreadFactory(threadFactory);
    result.setHandshakeTimeout(handshakeTimeout);
    result.setRequestedHeartbeat(requestedHeartbeat);
    result.setShutdownExecutor(shutdownExecutor);
    result.setHeartbeatExecutor(heartbeatExecutor);
    result.setChannelRpcTimeout(channelRpcTimeout);
    result.setChannelShouldCheckRpcResponseType(channelShouldCheckRpcResponseType);
    result.setWorkPoolTimeout(workPoolTimeout);
    result.setErrorOnWriteListener(errorOnWriteListener);
    result.setTopologyRecoveryFilter(topologyRecoveryFilter);
    result.setConnectionRecoveryTriggeringCondition(connectionRecoveryTriggeringCondition);
    result.setTopologyRecoveryRetryHandler(topologyRecoveryRetryHandler);
    result.setRecoveredQueueNameSupplier(recoveredQueueNameSupplier);
    result.setTrafficListener(trafficListener);
    result.setCredentialsRefreshService(credentialsRefreshService);
    result.setMaxInboundMessageBodySize(maxInboundMessageBodySize);
    return result;
  }

  protected AMQConnection createConnection(
      ConnectionParams params, FrameHandler frameHandler, MetricsCollector metricsCollector) {
    return new AMQConnection(params, frameHandler, metricsCollector, observationCollector);
  }

  /**
   * Create a new broker connection.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Reconnection attempts will always use the address configured on {@link ConnectionFactory}.
   *
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection() throws IOException, TimeoutException {
    return newConnection(
        this.sharedExecutor, Collections.singletonList(new Address(getHost(), getPort())));
  }

  /**
   * Create a new broker connection.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Reconnection attempts will always use the address configured on {@link ConnectionFactory}.
   *
   * @param connectionName client-provided connection name (an arbitrary string). Will be displayed
   *     in management UI if the server supports it.
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(String connectionName) throws IOException, TimeoutException {
    return newConnection(
        this.sharedExecutor,
        Collections.singletonList(new Address(getHost(), getPort())),
        connectionName);
  }

  /**
   * Create a new broker connection.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Reconnection attempts will always use the address configured on {@link ConnectionFactory}.
   *
   * @param executor thread execution service for consumers on the connection
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(ExecutorService executor) throws IOException, TimeoutException {
    return newConnection(executor, Collections.singletonList(new Address(getHost(), getPort())));
  }

  /**
   * Create a new broker connection.
   *
   * <p>If <a href="https://www.rabbitmq.com/api-guide.html#recovery">automatic connection
   * recovery</a> is enabled, the connection returned by this method will be {@link Recoverable}.
   * Reconnection attempts will always use the address configured on {@link ConnectionFactory}.
   *
   * @param executor thread execution service for consumers on the connection
   * @param connectionName client-provided connection name (an arbitrary string). Will be displayed
   *     in management UI if the server supports it.
   * @return an interface to the connection
   * @throws IOException if it encounters a problem
   */
  public Connection newConnection(ExecutorService executor, String connectionName)
      throws IOException, TimeoutException {
    return newConnection(
        executor, Collections.singletonList(new Address(getHost(), getPort())), connectionName);
  }

  protected AddressResolver createAddressResolver(List<Address> addresses) {
    if (addresses == null || addresses.isEmpty()) {
      throw new IllegalArgumentException("Please provide at least one address to connect to");
    } else if (addresses.size() > 1) {
      return new ListAddressResolver(addresses);
    } else {
      return new DnsRecordIpAddressResolver(addresses.get(0), isSSL());
    }
  }

  @Override
  public ConnectionFactory clone() {
    try {
      ConnectionFactory clone = (ConnectionFactory) super.clone();
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Load settings from a property file. Keys must be prefixed with <code>rabbitmq.</code>, use
   * {@link ConnectionFactory#load(String, String)} to specify your own prefix.
   *
   * @param propertyFileLocation location of the property file to use
   * @throws IOException when something goes wrong reading the file
   * @since 4.4.0
   * @see ConnectionFactoryConfigurator
   */
  public ConnectionFactory load(String propertyFileLocation) throws IOException {
    ConnectionFactoryConfigurator.load(this, propertyFileLocation);
    return this;
  }

  /**
   * Load settings from a property file.
   *
   * @param propertyFileLocation location of the property file to use
   * @param prefix key prefix for the entries in the file
   * @throws IOException when something goes wrong reading the file
   * @since 4.4.0
   * @see ConnectionFactoryConfigurator
   */
  public ConnectionFactory load(String propertyFileLocation, String prefix) throws IOException {
    ConnectionFactoryConfigurator.load(this, propertyFileLocation, prefix);
    return this;
  }

  /**
   * Load settings from a {@link Properties} instance. Keys must be prefixed with <code>rabbitmq.
   * </code>, use {@link ConnectionFactory#load(Properties, String)} to specify your own prefix.
   *
   * @param properties source for settings
   * @since 4.4.0
   * @see ConnectionFactoryConfigurator
   */
  public ConnectionFactory load(Properties properties) {
    ConnectionFactoryConfigurator.load(this, properties);
    return this;
  }

  /**
   * Load settings from a {@link Properties} instance.
   *
   * @param properties source for settings
   * @param prefix key prefix for properties entries
   * @since 4.4.0
   * @see ConnectionFactoryConfigurator
   */
  @SuppressWarnings("unchecked")
  public ConnectionFactory load(Properties properties, String prefix) {
    ConnectionFactoryConfigurator.load(this, (Map) properties, prefix);
    return this;
  }

  /**
   * Load settings from a {@link Map} instance. Keys must be prefixed with <code>rabbitmq.</code>,
   * use {@link ConnectionFactory#load(Map, String)} to specify your own prefix.
   *
   * @param properties source for settings
   * @since 4.4.0
   * @see ConnectionFactoryConfigurator
   */
  public ConnectionFactory load(Map<String, String> properties) {
    ConnectionFactoryConfigurator.load(this, properties);
    return this;
  }

  /**
   * Load settings from a {@link Map} instance.
   *
   * @param properties source for settings
   * @param prefix key prefix for map entries
   * @since 4.4.0
   * @see ConnectionFactoryConfigurator
   */
  public ConnectionFactory load(Map<String, String> properties, String prefix) {
    ConnectionFactoryConfigurator.load(this, properties, prefix);
    return this;
  }

  /**
   * Returns automatic connection recovery interval in milliseconds.
   *
   * @return how long will automatic recovery wait before attempting to reconnect, in ms; default is
   *     5000
   */
  public long getNetworkRecoveryInterval() {
    return networkRecoveryInterval;
  }

  /**
   * Sets connection recovery interval. Default is 5000. Uses {@link
   * com.rabbitmq.client.RecoveryDelayHandler.DefaultRecoveryDelayHandler} by default. Use another
   * {@link RecoveryDelayHandler} implementation for more flexibility.
   *
   * @param networkRecoveryInterval how long will automatic recovery wait before attempting to
   *     reconnect, in ms
   * @see RecoveryDelayHandler
   */
  public void setNetworkRecoveryInterval(int networkRecoveryInterval) {
    this.networkRecoveryInterval = networkRecoveryInterval;
  }

  /**
   * Sets connection recovery interval. Default is 5000. Uses {@link
   * com.rabbitmq.client.RecoveryDelayHandler.DefaultRecoveryDelayHandler} by default. Use another
   * {@link RecoveryDelayHandler} implementation for more flexibility.
   *
   * @param networkRecoveryInterval how long will automatic recovery wait before attempting to
   *     reconnect, in ms
   * @see RecoveryDelayHandler
   */
  public void setNetworkRecoveryInterval(long networkRecoveryInterval) {
    this.networkRecoveryInterval = networkRecoveryInterval;
  }

  /**
   * Returns automatic connection recovery delay handler.
   *
   * @return recovery delay handler. May be null if not set.
   * @since 4.3.0
   */
  public RecoveryDelayHandler getRecoveryDelayHandler() {
    return recoveryDelayHandler;
  }

  /**
   * Sets the automatic connection recovery delay handler.
   *
   * @param recoveryDelayHandler the recovery delay handler
   * @since 4.3.0
   */
  public void setRecoveryDelayHandler(final RecoveryDelayHandler recoveryDelayHandler) {
    this.recoveryDelayHandler = recoveryDelayHandler;
  }

  /**
   * Set the continuation timeout for RPC calls in channels. Default is 10 minutes. 0 means no
   * timeout.
   *
   * @param channelRpcTimeout
   */
  public void setChannelRpcTimeout(int channelRpcTimeout) {
    if (channelRpcTimeout < 0) {
      throw new IllegalArgumentException("Timeout cannot be less than 0");
    }
    this.channelRpcTimeout = channelRpcTimeout;
  }

  /**
   * Get the timeout for RPC calls in channels.
   *
   * @return
   */
  public int getChannelRpcTimeout() {
    return channelRpcTimeout;
  }

  /**
   * The factory to create SSL contexts. This provides more flexibility to create {@link
   * SSLContext}s for different connections than sharing the {@link SSLContext} with all the
   * connections produced by the connection factory (which is the case with the {@link
   * #useSslProtocol()} methods). This way, different connections with a different certificate for
   * each of them is a possible scenario.
   *
   * @param sslContextFactory
   * @see #useSslProtocol(SSLContext)
   * @since 5.0.0
   */
  public void setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
  }

  /**
   * When set to true, channels will check the response type (e.g. queue.declare expects a
   * queue.declare-ok response) of RPC calls and ignore those that do not match. Default is false.
   *
   * @param channelShouldCheckRpcResponseType
   */
  public void setChannelShouldCheckRpcResponseType(boolean channelShouldCheckRpcResponseType) {
    this.channelShouldCheckRpcResponseType = channelShouldCheckRpcResponseType;
  }

  public boolean isChannelShouldCheckRpcResponseType() {
    return channelShouldCheckRpcResponseType;
  }

  /**
   * Timeout (in ms) for work pool enqueueing. The {@link com.rabbitmq.client.impl.WorkPool}
   * dispatches several types of responses from the broker (e.g. deliveries). A high-traffic client
   * with slow consumers can exhaust the work pool and compromise the whole connection (by e.g.
   * letting the broker saturate the receive TCP buffers). Setting a timeout would make the
   * connection fail early and avoid hard-to-diagnose TCP connection failure. Note this shouldn't
   * happen with clients that set appropriate QoS values. Default is no timeout.
   *
   * @param workPoolTimeout timeout in ms
   * @since 4.5.0
   */
  public void setWorkPoolTimeout(int workPoolTimeout) {
    this.workPoolTimeout = workPoolTimeout;
  }

  public int getWorkPoolTimeout() {
    return workPoolTimeout;
  }

  /**
   * Set a listener to be called when connection gets an IO error trying to write on the socket.
   * Default listener triggers connection recovery asynchronously and propagates the exception.
   * Override the default listener to disable or customise automatic connection triggering on write
   * operations.
   *
   * @param errorOnWriteListener the listener
   * @since 4.5.0
   */
  public void setErrorOnWriteListener(ErrorOnWriteListener errorOnWriteListener) {
    this.errorOnWriteListener = errorOnWriteListener;
  }

  /**
   * Set filter to include/exclude entities from topology recovery.
   *
   * @since 4.8.0
   */
  public void setTopologyRecoveryFilter(TopologyRecoveryFilter topologyRecoveryFilter) {
    this.topologyRecoveryFilter = topologyRecoveryFilter;
  }

  /**
   * Allows to decide on automatic connection recovery is triggered. Default is for shutdown not
   * initiated by application or missed heartbeat errors.
   *
   * @param connectionRecoveryTriggeringCondition
   */
  public void setConnectionRecoveryTriggeringCondition(
      Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition) {
    this.connectionRecoveryTriggeringCondition = connectionRecoveryTriggeringCondition;
  }

  /**
   * Set retry handler for topology recovery. Default is no retry.
   *
   * @param topologyRecoveryRetryHandler
   * @since 5.4.0
   */
  public void setTopologyRecoveryRetryHandler(RetryHandler topologyRecoveryRetryHandler) {
    this.topologyRecoveryRetryHandler = topologyRecoveryRetryHandler;
  }

  /**
   * Set the recovered queue name supplier. Default is use the same queue name when recovering
   * queues.
   *
   * @param recoveredQueueNameSupplier queue name supplier
   */
  public void setRecoveredQueueNameSupplier(RecoveredQueueNameSupplier recoveredQueueNameSupplier) {
    this.recoveredQueueNameSupplier = recoveredQueueNameSupplier;
  }

  /**
   * Traffic listener notified of inbound and outbound {@link Command}s.
   *
   * <p>Useful for debugging purposes, e.g. logging all sent and received messages. Default is
   * no-op.
   *
   * @param trafficListener
   * @see TrafficListener
   * @see com.rabbitmq.client.impl.LogTrafficListener
   * @since 5.5.0
   */
  public void setTrafficListener(TrafficListener trafficListener) {
    this.trafficListener = trafficListener;
  }

  public static int ensureUnsignedShort(int value) {
    if (value < 0) {
      return 0;
    } else if (value > MAX_UNSIGNED_SHORT) {
      return MAX_UNSIGNED_SHORT;
    } else {
      return value;
    }
  }
}
