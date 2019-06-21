// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.RecoveryDelayHandler;
import com.rabbitmq.client.RecoveryDelayHandler.DefaultRecoveryDelayHandler;
import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.TrafficListener;
import com.rabbitmq.client.impl.recovery.RetryHandler;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;

public class ConnectionParams {
    private CredentialsProvider credentialsProvider;
    private ExecutorService consumerWorkServiceExecutor;
    private ScheduledExecutorService heartbeatExecutor;
    private ExecutorService shutdownExecutor;
    private String virtualHost;
    private Map<String, Object> clientProperties;
    private int requestedFrameMax;
    private int requestedChannelMax;
    private int requestedHeartbeat;
    private int handshakeTimeout;
    private int shutdownTimeout;
    private SaslConfig saslConfig;
    private long networkRecoveryInterval;
    private RecoveryDelayHandler recoveryDelayHandler;
    private boolean topologyRecovery;
    private ExecutorService topologyRecoveryExecutor;
    private int channelRpcTimeout;
    private boolean channelShouldCheckRpcResponseType;
    private ErrorOnWriteListener errorOnWriteListener;
    private int workPoolTimeout = -1;
    private TopologyRecoveryFilter topologyRecoveryFilter;
    private Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition;
    private RetryHandler topologyRecoveryRetryHandler;

    private ExceptionHandler exceptionHandler;
    private ThreadFactory threadFactory;

    private TrafficListener trafficListener;

    private CredentialsRefreshService credentialsRefreshService;

    public ConnectionParams() {}

    public CredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public ExecutorService getConsumerWorkServiceExecutor() {
        return consumerWorkServiceExecutor;
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
    
    /**
     * Get the recovery delay handler.
     * @return recovery delay handler or if none was set a {@link DefaultRecoveryDelayHandler} will be returned with a delay of {@link #getNetworkRecoveryInterval()}.
     */
    public RecoveryDelayHandler getRecoveryDelayHandler() {
        return recoveryDelayHandler == null ? new DefaultRecoveryDelayHandler(networkRecoveryInterval) : recoveryDelayHandler;
    }

    public boolean isTopologyRecoveryEnabled() {
        return topologyRecovery;
    }
    
    /**
     * Get the topology recovery executor. If null, the main connection thread should be used.
     * @return executor. May be null.
     */
    public ExecutorService getTopologyRecoveryExecutor() {
        return topologyRecoveryExecutor;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public int getChannelRpcTimeout() {
        return channelRpcTimeout;
    }

    public boolean channelShouldCheckRpcResponseType() {
        return channelShouldCheckRpcResponseType;
    }

    public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public void setConsumerWorkServiceExecutor(ExecutorService consumerWorkServiceExecutor) {
        this.consumerWorkServiceExecutor = consumerWorkServiceExecutor;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public void setClientProperties(Map<String, Object> clientProperties) {
        this.clientProperties = clientProperties;
    }

    public void setRequestedFrameMax(int requestedFrameMax) {
        this.requestedFrameMax = requestedFrameMax;
    }

    public void setRequestedChannelMax(int requestedChannelMax) {
        this.requestedChannelMax = requestedChannelMax;
    }

    public void setRequestedHeartbeat(int requestedHeartbeat) {
        this.requestedHeartbeat = requestedHeartbeat;
    }

    public void setShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public void setSaslConfig(SaslConfig saslConfig) {
        this.saslConfig = saslConfig;
    }

    public void setNetworkRecoveryInterval(long networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }
    
    public void setRecoveryDelayHandler(final RecoveryDelayHandler recoveryDelayHandler) {
        this.recoveryDelayHandler = recoveryDelayHandler;
    }

    public void setTopologyRecovery(boolean topologyRecovery) {
        this.topologyRecovery = topologyRecovery;
    }
    
    public void setTopologyRecoveryExecutor(final ExecutorService topologyRecoveryExecutor) {
        this.topologyRecoveryExecutor = topologyRecoveryExecutor;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    public ExecutorService getShutdownExecutor() {
        return shutdownExecutor;
    }

    public void setShutdownExecutor(ExecutorService shutdownExecutor) {
        this.shutdownExecutor = shutdownExecutor;
    }

    public ScheduledExecutorService getHeartbeatExecutor() {
        return heartbeatExecutor;
    }

    public void setHeartbeatExecutor(ScheduledExecutorService heartbeatExecutor) {
        this.heartbeatExecutor = heartbeatExecutor;
    }

    public void setChannelRpcTimeout(int channelRpcTimeout) {
        this.channelRpcTimeout = channelRpcTimeout;
    }

    public void setChannelShouldCheckRpcResponseType(boolean channelShouldCheckRpcResponseType) {
        this.channelShouldCheckRpcResponseType = channelShouldCheckRpcResponseType;
    }

    public void setErrorOnWriteListener(ErrorOnWriteListener errorOnWriteListener) {
        this.errorOnWriteListener = errorOnWriteListener;
    }

    public ErrorOnWriteListener getErrorOnWriteListener() {
        return errorOnWriteListener;
    }

    public void setWorkPoolTimeout(int workPoolTimeout) {
        this.workPoolTimeout = workPoolTimeout;
    }

    public int getWorkPoolTimeout() {
        return workPoolTimeout;
    }

    public void setTopologyRecoveryFilter(TopologyRecoveryFilter topologyRecoveryFilter) {
        this.topologyRecoveryFilter = topologyRecoveryFilter;
    }

    public TopologyRecoveryFilter getTopologyRecoveryFilter() {
        return topologyRecoveryFilter;
    }

    public void setConnectionRecoveryTriggeringCondition(Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition) {
        this.connectionRecoveryTriggeringCondition = connectionRecoveryTriggeringCondition;
    }

    public Predicate<ShutdownSignalException> getConnectionRecoveryTriggeringCondition() {
        return connectionRecoveryTriggeringCondition;
    }

    public void setTopologyRecoveryRetryHandler(RetryHandler topologyRecoveryRetryHandler) {
        this.topologyRecoveryRetryHandler = topologyRecoveryRetryHandler;
    }

    public RetryHandler getTopologyRecoveryRetryHandler() {
        return topologyRecoveryRetryHandler;
    }

    public void setTrafficListener(TrafficListener trafficListener) {
        this.trafficListener = trafficListener;
    }

    public TrafficListener getTrafficListener() {
        return trafficListener;
    }

    public void setCredentialsRefreshService(CredentialsRefreshService credentialsRefreshService) {
        this.credentialsRefreshService = credentialsRefreshService;
    }

    public CredentialsRefreshService getCredentialsRefreshService() {
        return credentialsRefreshService;
    }
}
