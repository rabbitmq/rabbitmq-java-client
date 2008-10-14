package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.GetResponse;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * Functional test to verify fanout behaviour
 */
public class FanoutTest extends BrokerTestCase {

    private static final int N = 10;

    protected static final String X = "amq.fanout";
    protected static final String K = "";

    protected static final byte[] payload = (""+ System.currentTimeMillis()).getBytes();

    // TODO: This setup code is copy and paste - maybe this should wander up to the super class?
    protected void setUp() throws IOException {
        openConnection();
        openChannel();
    }

    protected void tearDown() throws IOException {

        closeChannel();
        closeConnection();
    }

    public void testFanout() throws Exception {

        List<String> queues = new ArrayList<String>();

        for (int i = 0; i < N; i++) {
            String q = "Q-" + System.nanoTime();
            channel.queueDeclare(ticket, q);
            channel.queueBind(ticket, q, X, K);
            queues.add(q);
        }        

        channel.basicPublish(ticket, X, System.nanoTime() + "", MessageProperties.BASIC, payload);

        for (String q : queues) {
            GetResponse response = channel.basicGet(ticket, q, true);
            assertNotNull("The response should not be null", response);
        }

        for (String q : queues) {
            channel.queueDelete(ticket, q);
        }
    }
}
