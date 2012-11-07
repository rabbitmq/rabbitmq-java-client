package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.util.HashMap;
import java.util.Map;

public class DeadLetterExchangeDurable extends BrokerTestCase {
    public void testDeadLetterQueueTTLExpiredWhileDown() throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 5000);
        args.put("x-dead-letter-exchange", DeadLetterExchange.DLX);

        channel.exchangeDeclare(DeadLetterExchange.DLX, "direct", true);
        channel.queueDeclare(DeadLetterExchange.DLQ, true, false, false, null);
        channel.queueDeclare(DeadLetterExchange.TEST_QUEUE_NAME, true, false, false, args);
        channel.queueBind(DeadLetterExchange.TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DeadLetterExchange.DLQ, DeadLetterExchange.DLX, "test");

        for(int x = 0; x < DeadLetterExchange.MSG_COUNT; x++) {
            channel.basicPublish("amq.direct", "test", MessageProperties.MINIMAL_PERSISTENT_BASIC, "test message".getBytes());
        }

        closeConnection();
        Host.executeCommand("cd ../rabbitmq-test; make stop-app");
        Thread.sleep(5000);
        Host.executeCommand("cd ../rabbitmq-test; make start-app");
        openConnection();
        openChannel();

        DeadLetterExchange.consume(channel, "expired");
        channel.exchangeDelete(DeadLetterExchange.DLX);
        channel.queueDelete(DeadLetterExchange.DLQ);
        channel.queueDelete(DeadLetterExchange.TEST_QUEUE_NAME);
    }
}
