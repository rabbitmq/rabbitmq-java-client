package com.rabbitmq.client.test.performance;

/**
 * Tests routing latency with rate limiting
 */
public class RateLimitedRoutingTest extends BaseRoutingRateTest {

    public static void main(String[] args) throws Exception {

        int queue_weight = 10, binding_weight = 10, messages = 200;
        boolean topic = false;
        boolean rateLimited = true;
        boolean consumerMeasured = true;

        int iterations = 1;

        for (int i = 0 ; i < iterations ; i++) {
            int limit = (iterations - i);
            RateLimitedRoutingTest test = new RateLimitedRoutingTest();
            test.runStrategy(limit * binding_weight, limit * queue_weight, messages, topic, consumerMeasured, rateLimited);
        }

    }
}
