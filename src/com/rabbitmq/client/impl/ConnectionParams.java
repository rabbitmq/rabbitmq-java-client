package com.rabbitmq.client.impl;

import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.SaslConfig;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

public class ConnectionParams {
    private final String username;
    private final String password;
    private final ExecutorService executor;
    private final String virtualHost;
    private final Map<String, Object> clientProperties;
    private final int requestedFrameMax;
    private final int requestedChannelMax;
    private final int requestedHeartbeat;
    private int handshakeTimeout;
    private final int shutdownTimeout;
    private final SaslConfig saslConfig;
    private final long networkRecoveryInterval;
    private final boolean topologyRecovery;

    private final ExceptionHandler exceptionHandler;
    private final ThreadFactory threadFactory;

  /**
     * @param username name used to establish connection
     * @param password for <code><b>username</b></code>
     * @param executor thread pool service for consumer threads for channels on this connection
     * @param virtualHost virtual host of this connection
     * @param clientProperties client info used in negotiating with the server
     * @param requestedFrameMax max size of frame offered
     * @param requestedChannelMax max number of channels offered
     * @param requestedHeartbeat heart-beat in seconds offered
     * @param saslConfig sasl configuration hook
     * @param networkRecoveryInterval interval used when recovering from network failure
     * @param topologyRecovery should topology (queues, exchanges, bindings, consumers) recovery be performed?
     * @param threadFactory factory that instantiates threads used by the client
     * @param exceptionHandler handles unhandled consumer exceptions
     */
    public ConnectionParams(String username, String password, ExecutorService executor,
                            String virtualHost, Map<String, Object> clientProperties,
                            int requestedFrameMax, int requestedChannelMax, int requestedHeartbeat,
                            int shutdownTimeout, SaslConfig saslConfig, long networkRecoveryInterval,
                            boolean topologyRecovery, ExceptionHandler exceptionHandler, ThreadFactory threadFactory) {
        this.username = username;
        this.password = password;
        this.executor = executor;
        this.virtualHost = virtualHost;
        this.clientProperties = clientProperties;
        this.requestedFrameMax = requestedFrameMax;
        this.requestedChannelMax = requestedChannelMax;
        this.requestedHeartbeat = requestedHeartbeat;
        this.shutdownTimeout = shutdownTimeout;
        this.saslConfig = saslConfig;
        this.networkRecoveryInterval = networkRecoveryInterval;
        this.topologyRecovery = topologyRecovery;
        this.exceptionHandler = exceptionHandler;
        this.threadFactory = threadFactory;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public Map<String, Object> getClientProperties() {
        return clientProperties;
    }

    public int getRequestedFrameMax() {
        return requestedFrameMax;
    }

    public int getRequestedChannelMax() {
        return requestedChannelMax;
    }

    public int getRequestedHeartbeat() {
        return requestedHeartbeat;
    }

    public int getHandshakeTimeout() {
        return handshakeTimeout;
    }

    public void setHandshakeTimeout(int timeout) {
      this.handshakeTimeout = timeout;
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    public SaslConfig getSaslConfig() {
        return saslConfig;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public long getNetworkRecoveryInterval() {
        return networkRecoveryInterval;
    }

    public boolean isTopologyRecoveryEnabled() {
        return topologyRecovery;
    }

  public ThreadFactory getThreadFactory() {
    return threadFactory;
  }
}
