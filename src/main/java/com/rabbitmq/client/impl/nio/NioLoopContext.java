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

import com.rabbitmq.client.impl.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
public class NioLoopContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioLoopContext.class);

    private final SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory;

    private final ExecutorService executorService;

    private final ThreadFactory threadFactory;

    final ByteBuffer readBuffer, writeBuffer;

    SelectorHolder readSelectorState;
    SelectorHolder writeSelectorState;

    public NioLoopContext(SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory,
        NioParams nioParams) {
        this.socketChannelFrameHandlerFactory = socketChannelFrameHandlerFactory;
        this.executorService = nioParams.getNioExecutor();
        this.threadFactory = nioParams.getThreadFactory();
        NioContext nioContext = new NioContext(nioParams, null);
        this.readBuffer = nioParams.getByteBufferFactory().createReadBuffer(nioContext);
        this.writeBuffer = nioParams.getByteBufferFactory().createWriteBuffer(nioContext);
    }

    void initStateIfNecessary() throws IOException {
        // FIXME this should be synchronized
        if (this.readSelectorState == null) {
            this.readSelectorState = new SelectorHolder(Selector.open());
            this.writeSelectorState = new SelectorHolder(Selector.open());

            startIoLoops();
        }
    }

    private void startIoLoops() {
        if (executorService == null) {
            Thread nioThread = Environment.newThread(
                threadFactory,
                new NioLoop(socketChannelFrameHandlerFactory.nioParams, this),
                "rabbitmq-nio"
            );
            nioThread.start();
        } else {
            this.executorService.submit(new NioLoop(socketChannelFrameHandlerFactory.nioParams, this));
        }
    }

    protected boolean cleanUp() {
        int readRegistrationsCount = readSelectorState.registrations.size();
        if(readRegistrationsCount != 0) {
            return false;
        }
        socketChannelFrameHandlerFactory.lock();
        try {
            if (readRegistrationsCount != readSelectorState.registrations.size()) {
                // a connection request has come in meanwhile, don't do anything
                return false;
            }

            try {
                readSelectorState.selector.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close read selector: {}", e.getMessage());
            }
            try {
                writeSelectorState.selector.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close write selector: {}", e.getMessage());
            }

            this.readSelectorState = null;
            this.writeSelectorState = null;
        } finally {
            socketChannelFrameHandlerFactory.unlock();
        }
        return true;
    }
}
