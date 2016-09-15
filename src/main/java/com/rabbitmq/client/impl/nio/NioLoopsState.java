package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.impl.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class NioLoopsState {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioLoopsState.class);

    private final SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory;

    private final ExecutorService executorService;

    private final ThreadFactory threadFactory;

    private Thread readThread, writeThread;

    private Future<?> writeTask;

    SelectorHolder readSelectorState;
    SelectorHolder writeSelectorState;

    private final AtomicLong nioLoopsConnectionCount = new AtomicLong();

    public NioLoopsState(SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory,
        ExecutorService executorService, ThreadFactory threadFactory) {
        this.socketChannelFrameHandlerFactory = socketChannelFrameHandlerFactory;
        this.executorService = executorService;
        this.threadFactory = threadFactory;
    }

    void notifyNewConnection() {
        nioLoopsConnectionCount.incrementAndGet();
    }

    void initStateIfNecessary() throws IOException {
        if(this.readSelectorState == null) {
            this.readSelectorState = new SelectorHolder(Selector.open());
            this.writeSelectorState = new SelectorHolder(Selector.open());

            startIoLoops();
        }
    }

    private void startIoLoops() {
        if(executorService == null) {
            this.readThread = Environment.newThread(
                threadFactory,
                new ReadLoop(socketChannelFrameHandlerFactory.nioParams, this),
                "rabbitmq-nio-read"
            );
            this.writeThread = Environment.newThread(
                threadFactory,
                new WriteLoop(socketChannelFrameHandlerFactory.nioParams,this.writeSelectorState),
                "rabbitmq-nio-write"
            );
            readThread.start();
            writeThread.start();
        } else {
            this.executorService.submit(new ReadLoop(socketChannelFrameHandlerFactory.nioParams, this));
            this.writeTask = this.executorService.submit(new WriteLoop(socketChannelFrameHandlerFactory.nioParams,this.writeSelectorState));
        }
    }

    protected boolean cleanUp() {
        long connectionCountNow = nioLoopsConnectionCount.get();
        socketChannelFrameHandlerFactory.lock();
        try {
            if(connectionCountNow != nioLoopsConnectionCount.get()) {
                // a connection request has come in meanwhile, don't do anything
                return false;
            }

            if(this.executorService == null) {
                this.writeThread.interrupt();
            } else {
                boolean canceled = this.writeTask.cancel(true);
                if(!canceled) {
                    LOGGER.info("Could not stop write NIO task");
                }
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
