package com.rabbitmq.client;

import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.CredentialsRefreshService;
import com.rabbitmq.client.impl.DefaultCredentialsRefreshService;
import com.rabbitmq.client.impl.OAuth2ClientCredentialsGrantCredentialsProvider;
import com.rabbitmq.client.impl.nio.NioParams;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static com.rabbitmq.client.impl.DefaultCredentialsRefreshService.ratioRefreshDelayStrategy;

public class ConnectionFactoryConfigurationDemo {

  public static void main(String[] args) throws Exception {
    SSLContext sslContext = SSLContext.getDefault();

    // historical configuration with ConnectionFactory setters
    ConnectionFactory cf = new ConnectionFactory();
    cf.setUri("amqp://rabbitmq-1:5672/foo");
    cf.setChannelRpcTimeout(10_000); // unit?
    Map<String, Object> clientProperties = Collections.singletonMap("foo", "bar");
    cf.setClientProperties(clientProperties);
    cf.useSslProtocol("TLSv1.3", new TrustEverythingTrustManager());
    NioParams nioParams = new NioParams();
    nioParams.setNbIoThreads(4);
    cf.setNioParams(nioParams);

    CredentialsProvider credentialsProvider =
        new OAuth2ClientCredentialsGrantCredentialsProvider.OAuth2ClientCredentialsGrantCredentialsProviderBuilder()
            .tokenEndpointUri("http://localhost:8080/uaa/oauth/token/")
            .clientId("rabbit_client").clientSecret("rabbit_secret")
            .grantType("password")
            .parameter("username", "rabbit_super")
            .parameter("password", "rabbit_super")
              .tls()
              .sslContext(sslContext)
              .builder()
            .build();
    cf.setCredentialsProvider(credentialsProvider);
    CredentialsRefreshService refreshService =
        new DefaultCredentialsRefreshService.DefaultCredentialsRefreshServiceBuilder()
            .refreshDelayStrategy(ratioRefreshDelayStrategy(0.8))
            .build();
    cf.setCredentialsRefreshService(refreshService);

    // configuration with new configuration API
    ConnectionFactory.configure()
        .uri("amqp://rabbitmq-1:5672/foo")
        .channelRpcTimeout(Duration.ofSeconds(10)) // Duration class instead of int
        .clientProperty("foo", "bar")
        .tls()  // TLS configuration API
          .protocol("TLSv1.3")
          .trustEverything()
          .configuration()  // back to main configuration
        .nio()  // NIO configuration API
          .nbIoThreads(4)
          .configuration()  // back to main configuration
        .oauth2()  // OAuth 2 configuration API
          .tokenEndpointUri("http://localhost:8080/uaa/oauth/token/")
          .clientId("rabbit_client").clientSecret("rabbit_secret")
          .grantType("password")
          .parameter("username", "rabbit_super")
          .parameter("password", "rabbit_super")
          .tls()  // OAuth 2 TLS
            .sslContext(sslContext)
            .oauth2()
          .refresh()  // OAuth refresh configuration
            .refreshDelayStrategy(ratioRefreshDelayStrategy(0.8))
            .oauth2()
          .configuration()  // back to main configuration
        .create();
  }

}
