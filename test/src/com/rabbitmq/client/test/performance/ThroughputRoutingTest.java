package com.rabbitmq.client.test.performance;

/**
 * Measures routing throughput
 */
public class ThroughputRoutingTest extends BaseRoutingRateTest {

    public static void main(String[] args) throws Exception {

        int queue_weight = 10, binding_weight = 10, messages = 1000;
        boolean topic = false;
        boolean rateLimited = false;
        boolean consumerMeasured = false;

        int iterations = 10;

        for (int i = 0 ; i < iterations ; i++) {
            int limit = (iterations - i);
            ThroughputRoutingTest test = new ThroughputRoutingTest();
            test.runStrategy(limit * binding_weight, limit * queue_weight, messages, topic, consumerMeasured, rateLimited);
        }

    }
}
