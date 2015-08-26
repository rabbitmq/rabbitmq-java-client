package com.rabbitmq.client.impl;

import com.rabbitmq.client.ShutdownSignalException;
import org.junit.Test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ChannelManagerTest {

    @Test
    public void testShouldPrefetchAThreadForShutdown() throws InterruptedException {
        System.out.println("start test");
        MyThreadFactory channelManagerThreadFactory = new MyThreadFactory();
        MyThreadFactory consumerWorkServiceThreadFactory = new MyThreadFactory();

        ConsumerWorkService consumerWorkService = new ConsumerWorkService(null, consumerWorkServiceThreadFactory, 10 * 1000);
        int channelMax = 10;

        ChannelManager channelManager = new ChannelManager(consumerWorkService, channelMax, channelManagerThreadFactory);

        assertEquals(0, consumerWorkServiceThreadFactory.newThreadsRequestedCount.get());
        assertEquals(1, channelManagerThreadFactory.newThreadsRequestedCount.get());

        TimeUnit.SECONDS.sleep(10);

        channelManager.handleSignal(new ShutdownSignalException(false, false, new AMQImpl.Basic.ConsumeOk("whatever"), null));

        TimeUnit.SECONDS.sleep(1);

        assertEquals(0, consumerWorkServiceThreadFactory.newThreadsRequestedCount.get());
        assertEquals(1, channelManagerThreadFactory.newThreadsRequestedCount.get());

        channelManager.handleSignal(new ShutdownSignalException(false, false, new AMQImpl.Basic.ConsumeOk("whatever"), null));

        assertEquals(0, consumerWorkServiceThreadFactory.newThreadsRequestedCount.get());
        assertEquals(1, channelManagerThreadFactory.newThreadsRequestedCount.get());

        System.out.println("end test");
    }

    private static class MyThreadFactory implements ThreadFactory {

        final AtomicInteger newThreadsRequestedCount = new AtomicInteger(0);

        public Thread newThread(Runnable r) {
            newThreadsRequestedCount.incrementAndGet();
            return new Thread(r);
        }
    }
}
