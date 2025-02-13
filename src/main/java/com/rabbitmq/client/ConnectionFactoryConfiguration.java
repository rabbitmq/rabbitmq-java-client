package com.rabbitmq.client;

import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.CredentialsRefreshService;
import com.rabbitmq.client.impl.ErrorOnWriteListener;
import com.rabbitmq.client.impl.nio.ByteBufferFactory;
import com.rabbitmq.client.impl.nio.NioContext;
import com.rabbitmq.client.impl.nio.NioQueue;
import com.rabbitmq.client.impl.recovery.RecoveredQueueNameSupplier;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;
import com.rabbitmq.client.observation.ObservationCollector;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public interface ConnectionFactoryConfiguration {

  ConnectionFactoryConfiguration host(String name);
  ConnectionFactoryConfiguration port(int port);
  ConnectionFactoryConfiguration username(String username);
  ConnectionFactoryConfiguration password(String username);
  ConnectionFactoryConfiguration virtualHost(String virtualHost);
  ConnectionFactoryConfiguration uri(URI uri);
  ConnectionFactoryConfiguration uri(String uri);

  ConnectionFactoryConfiguration requestedChannelMax(int requestedChannelMax);
  ConnectionFactoryConfiguration requestedFrameMax(int requestedFrameMax);
  ConnectionFactoryConfiguration requestedHeartbeat(Duration heartbeat);
  ConnectionFactoryConfiguration connectionTimeout(Duration timeout);
  ConnectionFactoryConfiguration handshakeTimeout(Duration timeout);
  ConnectionFactoryConfiguration shutdownTimeout(Duration timeout);
  ConnectionFactoryConfiguration channelRpcTimeout(Duration timeout);

  ConnectionFactoryConfiguration maxInboundMessageBodySize(int maxInboundMessageBodySize);
  ConnectionFactoryConfiguration channelShouldCheckRpcResponseType(boolean channelShouldCheckRpcResponseType);
  ConnectionFactoryConfiguration workPoolTimeout(Duration timeout);

  ConnectionFactoryConfiguration errorOnWriteListener(ErrorOnWriteListener errorOnWriteListener);

  ConnectionFactoryConfiguration trafficListener(TrafficListener trafficListener);

  // TODO provide helper for client properties
  ConnectionFactoryConfiguration clientProperties(Map<String, Object> clientProperties);
  ConnectionFactoryConfiguration clientProperty(String name, Object value);

  ConnectionFactoryConfiguration saslConfig(SaslConfig saslConfig);

  ConnectionFactoryConfiguration socketFactory(SocketFactory socketFactory);

  ConnectionFactoryConfiguration socketConfigurator(SocketConfigurator socketConfigurator);

  ConnectionFactoryConfiguration sharedExecutor(ExecutorService executorService);
  ConnectionFactoryConfiguration shutdownExecutor(ExecutorService executorService);
  ConnectionFactoryConfiguration heartbeatExecutor(ExecutorService executorService);
  ConnectionFactoryConfiguration threadFactory(ThreadFactory threadFactory);

  ConnectionFactoryConfiguration exceptionHandler(ExceptionHandler exceptionHandler);

  ConnectionFactoryConfiguration metricsCollector(MetricsCollector metricsCollector);
  ConnectionFactoryConfiguration observationCollector(ObservationCollector observationCollector);

  // TODO special configuration for credentials, especially for OAuth?
  ConnectionFactoryConfiguration credentialsProvider(CredentialsProvider credentialsProvider);
  ConnectionFactoryConfiguration credentialsRefreshService(CredentialsRefreshService credentialsRefreshService);

  NioConfiguration nio();

  TlsConfiguration tls();

  OAuth2Configuration oauth2();

  ConnectionFactory create();

  interface NioConfiguration {

    NioConfiguration readByteBufferSize(int readByteBufferSize);

    NioConfiguration writeByteBufferSize(int writeByteBufferSize);

    NioConfiguration nbIoThreads(int nbIoThreads);

    NioConfiguration writeEnqueuingTimeout(Duration writeEnqueuingTimeout);

    NioConfiguration writeQueueCapacity(int writeQueueCapacity);

    NioConfiguration executor(ExecutorService executorService);

    NioConfiguration threadFactory(ThreadFactory threadFactory);

    NioConfiguration socketChannelConfigurator(SocketChannelConfigurator configurator);

    NioConfiguration sslEngineConfigurator(SslEngineConfigurator configurator);

    NioConfiguration connectionShutdownExecutor(ExecutorService executorService);

    NioConfiguration byteBufferFactory(ByteBufferFactory byteBufferFactory);

    NioConfiguration writeQueueFactory(Function<NioContext, NioQueue> writeQueueFactory);

    ConnectionFactoryConfiguration configuration();


  }

  interface TlsConfiguration {

    TlsConfiguration hostnameVerification();

    TlsConfiguration hostnameVerification(boolean hostnameVerification);

    TlsConfiguration sslContextFactory(SslContextFactory sslContextFactory);

    TlsConfiguration protocol(String protocol);

    TlsConfiguration trustManager(TrustManager trustManager);

    TlsConfiguration trustEverything();

    TlsConfiguration sslContext(SSLContext sslContext);

    ConnectionFactoryConfiguration configuration();

  }

  interface RecoveryConfiguration {

    RecoveryConfiguration enableConnectionRecovery();
    RecoveryConfiguration enableConnectionRecovery(boolean connectionRecovery);

    RecoveryConfiguration enableTopologyRecovery();
    RecoveryConfiguration enableTopologyRecovery(boolean connectionRecovery);

    RecoveryConfiguration topologyRecoveryExecutor(ExecutorService executorService);

    RecoveryConfiguration recoveryInterval(Duration interval);

    RecoveryConfiguration recoveryDelayHandler(RecoveryDelayHandler recoveryDelayHandler);

    RecoveryConfiguration topologyRecoveryFilter(TopologyRecoveryFilter topologyRecoveryFilter);

    RecoveryConfiguration recoveryTriggeringCondition(Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition);

    RecoveryConfiguration recoveredQueueNameSupplier(RecoveredQueueNameSupplier recoveredQueueNameSupplier);

    ConnectionFactoryConfiguration configuration();
  }

  interface OAuth2Configuration {

    OAuth2Configuration tokenEndpointUri(String tokenEndpointUri);

    OAuth2Configuration clientId(String clientId);

    OAuth2Configuration clientSecret(String clientSecret);

    OAuth2Configuration grantType(String grantType);

    OAuth2Configuration parameter(String name, String value);

    OAuth2Configuration connectionConfigurator(Consumer<HttpURLConnection> connectionConfigurator);

    OAuth2TlsConfiguration tls();

    OAuth2CredentialsRefreshConfiguration refresh();

    ConnectionFactoryConfiguration configuration();
  }

  interface OAuth2TlsConfiguration {

    OAuth2TlsConfiguration hostnameVerifier(HostnameVerifier hostnameVerifier);

    OAuth2TlsConfiguration sslSocketFactory(SSLSocketFactory sslSocketFactory);

    OAuth2TlsConfiguration sslContext(SSLContext sslContext);

    OAuth2TlsConfiguration trustEverything();

    OAuth2Configuration oauth2();

  }

  interface OAuth2CredentialsRefreshConfiguration {

    OAuth2CredentialsRefreshConfiguration refreshDelayStrategy(Function<Duration, Duration> refreshDelayStrategy);

    OAuth2CredentialsRefreshConfiguration approachingExpirationStrategy(Function<Duration, Boolean> approachingExpirationStrategy);

    OAuth2CredentialsRefreshConfiguration scheduler(ScheduledThreadPoolExecutor scheduler);

    OAuth2Configuration oauth2();

  }

}
