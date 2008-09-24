package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Random;

/**
 * This test has 3 phases which each should be timed for different values for q, b and n:
 *
 * 1. Start a consumer thread, set up q queues with b bindings and subscribe to them
 * 2. Run a producer thread and send n messages
 * 3. Consumer thread should receive n messages
 * 4. Unsubscribe from all of the queues
 * 5. Delete all of the queues, thus unbinding everything
 *
 */
public class RoutingRateTest {


    final long rateLimit = 1000;
    final long interval = 50;

    private String[] bindings, queues;

    public static void main(String[] args) throws Exception {
        //strategy(100,100,1000, false);
        strategy(5,5,100, true);
    }

    private static void strategy(int b, int q, int n, boolean topic) throws Exception {
        RoutingRateTest smallTest = new RoutingRateTest();
        Parameters smallStats = smallTest.runTest(new Parameters(b, q, n), topic, true);
        smallStats.printStats();

        /*
        RoutingRateTest mediumTest = new RoutingRateTest();
        Parameters mediumStats = mediumTest.runTest(new Parameters(b, q, n * 2), topic);
        mediumStats.printStats();

        RoutingRateTest largeTest = new RoutingRateTest();
        Parameters largeStats = largeTest.runTest(new Parameters(b, q, n * 10), topic);
        largeStats.printStats();


        doFinalSummary(smallStats, mediumStats, largeStats);
        */
    }

    private static void doFinalSummary(Parameters... args) {
        System.err.println();
        System.err.println(".......");
        System.err.println("Final Summary......");
        System.err.println();

        for (Parameters s : args) {
            s.printStats();
        }
    }

    private Parameters runTest(Parameters parameters, boolean topic, boolean consumerMeasured) throws Exception {

        String postfix = (topic) ? ".*" : "";
        String type = (topic) ? "topic" : "direct";

        bindings = generate(parameters.b, "b.", postfix);
        queues = generate(parameters.q, "q-", "");

        String x = "x-" + System.currentTimeMillis();

        int bs  = 1000;

        final Connection con = new ConnectionFactory().newConnection("0.0.0.0", 5672);
        Channel channel = con.createChannel();
        channel.exchangeDeclare(1, x, type);

        parameters.bindingRate = declareAndBindQueues(x, bs, channel);


        ProducerThread producerRef = new ProducerThread(con.createChannel(), x, parameters.n, topic);
        Thread producer = new Thread(producerRef);

        if (consumerMeasured) {
            ConsumerThread consumerRef = new ConsumerThread(con.createChannel(), producer, parameters.n);
            Thread consumer = new Thread(consumerRef);
            consumer.start();
            consumer.join();
            parameters.consumerRate = consumerRef.rate;
        }

        else {
            producer.start();
            producer.join();
        }

        parameters.producerRate = producerRef.rate;
        parameters.unbindingRate = deleteQueues(channel);

        channel.close(200, "hasta la vista, baby");
        con.close();

        return parameters;
    }

    static class Parameters {

        int q,b,n;

        Parameters(int q, int b, int n) {
            this.q = q;
            this.b = b;
            this.n = n;
        }

        float consumerRate, producerRate, unbindingRate, bindingRate;

        void printStats() {
            System.err.println("----------------");
            System.err.println("SUMMARY (q = " + q + ", b = " + b + ", n = " + n + ")");
            System.err.println("Consumer -> " + consumerRate);
            System.err.println("Producer -> " + producerRate);
            System.err.println("Creation -> " + bindingRate);
            System.err.println("Nuking -> " + unbindingRate);

            System.err.println("----------------");
        }
    }

    private float declareAndBindQueues(String x, int bs, Channel channel) throws IOException {
        System.err.println("Creating queues ....... ");
        int cnt = 0;
        final long start = System.currentTimeMillis();
        long split = start;
        for (String queue : queues) {
            channel.queueDeclare(1, queue);
            for (String binding : bindings) {
                channel.queueBind(1, queue, x, binding);
                if ((++cnt % bs) == 0) {
                    long now = System.currentTimeMillis();
                    calculateRate("Creator-split", bs, now, split);
                    split = now;
                }
            }
        }
        final long now = System.currentTimeMillis();
        return calculateRate("Creator-overall", bindings.length * queues.length, now, start);
    }

    private float calculateRate(String who, int size, long now, long then) {
        float diff = (float)(now - then) / 1000;
        float rate = size  / diff;
        System.err.println(who + " : Rate = " + size  / diff);
        return rate;
    }

    private float deleteQueues(Channel channel) throws IOException {
        System.err.println("Deleting queues ....... ");
        long start = System.currentTimeMillis();
        for (String queue : queues) {
            channel.queueDelete(1, queue);
        }
        long now = System.currentTimeMillis();
        return calculateRate("Deleter", queues.length, now, start);
    }

    private String[] generate(int z, String prefix, String postfix) {
        String[] s =  new String[z];
        Random r = new Random();
        for (int i = 0; i < z; i++) {
            s[i] = prefix + r.nextLong() + postfix;
        }
        return s;
    }

    class ConsumerThread extends QueueingConsumer implements Runnable {

        final int count;
        Thread producer;
        float rate;

        ConsumerThread(Channel channel, Thread t, int messageCount) {
            super(channel);
            count = messageCount;
            producer = t;
        }

        public void run() {

            // Subscribe to each queue

            for (String queue : queues) {
                try {
                    getChannel().basicConsume(1,queue,this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // Start the producer thread now
            producer.start();

            final long start = System.currentTimeMillis();

            int n = count;
            while (n-- > 0) {
                try {
                    nextDelivery();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            final long now = System.currentTimeMillis();
            rate = calculateRate("Consumer", count, now, start);

            // Unsubscribe to each queue

            for (String queue : queues) {
                try {
                    getChannel().basicConsume(1,queue,this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    class ProducerThread implements Runnable {

        final int count;
        float rate;

        Channel c;
        String x;
        boolean topic;

        long lastStatsTime;

        int n;

        ProducerThread(Channel c, String x, int messageCount, boolean t) {
            this.c = c;
            this.x = x;
            this.topic = t;
            count = messageCount;

            try {
                c.exchangeDeclare(1, x, (t) ? "topic" : "direct");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        public void run() {


            n = count;

            long start = lastStatsTime = System.currentTimeMillis();

            doSelect();

            while (n-- > 0) {
                try {

                    send(c, x, topic);
                    delay(System.currentTimeMillis());

                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            doCommit();

            final long nownow = System.currentTimeMillis();
            rate = calculateRate("Producer", count, nownow, start);
        }

        private void delay(final long now) throws InterruptedException {

            final long elapsed = now - lastStatsTime;
            //example: rateLimit is 5000 msg/s,
            //10 ms have elapsed, we have sent 200 messages
            //the 200 msgs we have actually sent should have taken us
            //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
            final long pause = rateLimit == 0 ?
                0 : ( (count - n)  * 1000L / rateLimit - elapsed);
            if (pause > 0) {
                Thread.sleep(pause);
            }
            if (elapsed > interval) {
                System.out.println("sending rate: " +
                                   ( (count - n) * 1000 / elapsed) +
                                   " msg/s");
                lastStatsTime = now;
            }
        }


        private void doCommit() {
            try {
                c.txCommit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void doSelect() {
            try {
                // Who invented checked exceptions?
                c.txSelect();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void send(Channel channel, String x, boolean topic) throws IOException {
        byte[] payload = (System.nanoTime() + "-").getBytes();
        Random ran = new Random();
        String b = bindings[ran.nextInt(bindings.length )];
        String r = (topic) ? b.replace("*", System.currentTimeMillis() + "") : b;


        channel.basicPublish(1, x, r, MessageProperties.MINIMAL_BASIC, payload);
        
    }

}
