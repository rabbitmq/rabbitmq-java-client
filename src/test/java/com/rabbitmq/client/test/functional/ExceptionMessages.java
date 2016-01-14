package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.UUID;

public class ExceptionMessages extends BrokerTestCase {
    public void testAlreadyClosedExceptionMessageWithChannelError() throws IOException {
        String uuid = UUID.randomUUID().toString();
        try {
            channel.queueDeclarePassive(uuid);
            fail("expected queueDeclarePassive to throw");
        } catch (IOException e) {
            // ignored
        }

        try {
            channel.queueDeclarePassive(uuid);
            fail("expected queueDeclarePassive to throw");
        } catch (AlreadyClosedException ace) {
            assertTrue(ace.getMessage().startsWith("channel is already closed due to channel error"));
        }
    }

    public void testAlreadyClosedExceptionMessageWithCleanClose() throws IOException {
        String uuid = UUID.randomUUID().toString();

        try {
            channel.abort();
            channel.queueDeclare(uuid, false, false, false, null);
        } catch (AlreadyClosedException ace) {
            assertTrue(ace.getMessage().startsWith("channel is already closed due to clean channel shutdown"));
        }
    }
}
