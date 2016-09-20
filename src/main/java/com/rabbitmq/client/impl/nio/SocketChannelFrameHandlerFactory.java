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

package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SocketConfigurator;
import com.rabbitmq.client.impl.AbstractFrameHandlerFactory;
import com.rabbitmq.client.impl.FrameHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class SocketChannelFrameHandlerFactory extends AbstractFrameHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandlerFactory.class);

    private final ExecutorService executorService;

    private final ThreadFactory threadFactory;

    final NioParams nioParams;

    private final SSLContext sslContext;

    private final Lock stateLock = new ReentrantLock();

    private final AtomicLong globalConnectionCount = new AtomicLong();

    private final List<NioLoopsState> nioLoopsStates;

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator, NioParams nioParams, boolean ssl, SSLContext sslContext) throws IOException {
        super(connectionTimeout, configurator, ssl);
        this.nioParams = new NioParams(nioParams);
        this.sslContext = sslContext;
        this.executorService = nioParams.getNioExecutor();
        this.threadFactory = nioParams.getThreadFactory();
        this.nioLoopsStates = new ArrayList<NioLoopsState>(this.nioParams.getNbIoThreads() / 2);
        for(int i = 0; i < this.nioParams.getNbIoThreads() / 2; i++) {
            this.nioLoopsStates.add(new NioLoopsState(this, this.nioParams));
        }
    }

    @Override
    public FrameHandler create(Address addr) throws IOException {
        int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);

        SSLEngine sslEngine = null;
        if(ssl) {
            sslEngine = sslContext.createSSLEngine(addr.getHost(), portNumber);
            sslEngine.setUseClientMode(true);
        }

        SocketAddress address = new InetSocketAddress(addr.getHost(), portNumber);
        SocketChannel channel = SocketChannel.open();
        configurator.configure(channel.socket());

        // FIXME handle connection failure
        channel.connect(address);

        channel.configureBlocking(false);

        if(ssl) {
            sslEngine.beginHandshake();
            boolean handshake = SslEngineHelper.doHandshake(channel, sslEngine);
            if(!handshake) {
                throw new SSLException("TLS handshake failed");
            }
        }

        // lock
        stateLock.lock();
        NioLoopsState nioLoopsState = null;
        try {
            long modulo = globalConnectionCount.getAndIncrement() % (nioParams.getNbIoThreads() / 2);
            nioLoopsState = nioLoopsStates.get((int) modulo);
            nioLoopsState.initStateIfNecessary();
            nioLoopsState.notifyNewConnection();
        } finally {
            stateLock.unlock();
        }

        SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(
            channel,
            nioLoopsState,
            nioParams,
            sslEngine
        );

        SocketChannelFrameHandler frameHandler = new SocketChannelFrameHandler(state);
        return frameHandler;
    }

    void lock() {
        stateLock.lock();
    }

    void unlock() {
        stateLock.unlock();
    }

}
