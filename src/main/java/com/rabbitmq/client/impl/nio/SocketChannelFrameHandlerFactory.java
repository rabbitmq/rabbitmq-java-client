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
import com.rabbitmq.client.SslContextFactory;
import com.rabbitmq.client.impl.AbstractFrameHandlerFactory;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.TlsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class SocketChannelFrameHandlerFactory extends AbstractFrameHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandler.class);

    final NioParams nioParams;

    private final SslContextFactory sslContextFactory;

    private final Lock stateLock = new ReentrantLock();

    private final AtomicLong globalConnectionCount = new AtomicLong();

    private final List<NioLoopContext> nioLoopContexts;

    public SocketChannelFrameHandlerFactory(int connectionTimeout, NioParams nioParams, boolean ssl, SslContextFactory sslContextFactory)
        throws IOException {
        super(connectionTimeout, null, ssl);
        this.nioParams = new NioParams(nioParams);
        this.sslContextFactory = sslContextFactory;
        this.nioLoopContexts = new ArrayList<NioLoopContext>(this.nioParams.getNbIoThreads());
        for (int i = 0; i < this.nioParams.getNbIoThreads(); i++) {
            this.nioLoopContexts.add(new NioLoopContext(this, this.nioParams));
        }
    }

    @Override
    public FrameHandler create(Address addr, String connectionName) throws IOException {
        int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);

        SSLEngine sslEngine = null;
        SocketChannel channel = null;

        try {
            if (ssl) {
                SSLContext sslContext = sslContextFactory.create(connectionName);
                sslEngine = sslContext.createSSLEngine(addr.getHost(), portNumber);
                sslEngine.setUseClientMode(true);
                if (nioParams.getSslEngineConfigurator() != null) {
                    nioParams.getSslEngineConfigurator().configure(sslEngine);
                }
            }

            SocketAddress address = new InetSocketAddress(addr.getHost(), portNumber);
            // No Sonar: the channel is closed in case of error and it cannot
            // be closed here because it's part of the state of the connection
            // to be returned.
            channel = SocketChannel.open(); //NOSONAR
            channel.configureBlocking(true);
            if(nioParams.getSocketChannelConfigurator() != null) {
                nioParams.getSocketChannelConfigurator().configure(channel);
            }

            channel.connect(address);

            if (ssl) {
                sslEngine.beginHandshake();
                try {
                    boolean handshake = SslEngineHelper.doHandshake(channel, sslEngine);
                    if (!handshake) {
                        LOGGER.error("TLS connection failed");
                        throw new SSLException("TLS handshake failed");
                    }
                } catch (SSLHandshakeException e) {
                    LOGGER.error("TLS connection failed: {}", e.getMessage());
                    throw e;
                }
                TlsUtils.logPeerCertificateInfo(sslEngine.getSession());
            }

            channel.configureBlocking(false);

            // lock
            stateLock.lock();
            NioLoopContext nioLoopContext = null;
            try {
                long modulo = globalConnectionCount.getAndIncrement() % nioParams.getNbIoThreads();
                nioLoopContext = nioLoopContexts.get((int) modulo);
                nioLoopContext.initStateIfNecessary();
                SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(
                    channel,
                    nioLoopContext,
                    nioParams,
                    sslEngine
                );
                state.startReading();
                SocketChannelFrameHandler frameHandler = new SocketChannelFrameHandler(state);
                return frameHandler;
            } finally {
                stateLock.unlock();
            }


        } catch(IOException e) {
            try {
                if(sslEngine != null && channel != null) {
                    SslEngineHelper.close(channel, sslEngine);
                }
                if (channel != null) {
                    channel.close();
                }
            } catch(IOException closingException) {
                // ignore
            }
            throw e;
        }

    }

    void lock() {
        stateLock.lock();
    }

    void unlock() {
        stateLock.unlock();
    }
}
