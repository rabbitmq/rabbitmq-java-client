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
import com.rabbitmq.client.SaslConfig;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class ConnectionParams {
    private String username;
    private String password;
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
    private boolean topologyRecovery;
    private int channelRpcTimeout;
    private boolean channelCheckRpcReplyType;

    private ExceptionHandler exceptionHandler;
    private ThreadFactory threadFactory;

    public ConnectionParams() {}

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
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

    public boolean isTopologyRecoveryEnabled() {
        return topologyRecovery;
    }

    public ThreadFactory getThreadFactory() {
    return threadFactory;
  }

    public int getChannelRpcTimeout() {
        return channelRpcTimeout;
    }

    public boolean isChannelCheckRpcReplyType() {
        return channelCheckRpcReplyType;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public void setTopologyRecovery(boolean topologyRecovery) {
        this.topologyRecovery = topologyRecovery;
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

    public void setChannelCheckRpcReplyType(boolean channelCheckRpcReplyType) {
        this.channelCheckRpcReplyType = channelCheckRpcReplyType;
    }
}
