package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.impl.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

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

    private final AtomicLong nioLoopsConnectionCount = new AtomicLong();

    public NioLoopContext(SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory,
        NioParams nioParams) {
        this.socketChannelFrameHandlerFactory = socketChannelFrameHandlerFactory;
        this.executorService = nioParams.getNioExecutor();
        this.threadFactory = nioParams.getThreadFactory();
        this.readBuffer = ByteBuffer.allocate(nioParams.getReadByteBufferSize());
        this.writeBuffer = ByteBuffer.allocate(nioParams.getWriteByteBufferSize());
    }

    void notifyNewConnection() {
        nioLoopsConnectionCount.incrementAndGet();
    }

    void initStateIfNecessary() throws IOException {
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
        long connectionCountNow = nioLoopsConnectionCount.get();
        socketChannelFrameHandlerFactory.lock();
        try {
            if (connectionCountNow != nioLoopsConnectionCount.get()) {
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
