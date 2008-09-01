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

    private String[] bindings, queues;

    public static void main(String[] args) throws Exception {
        strategy(100,100,50);
    }

    private static void strategy(int b, int q, int n) throws Exception {
        RoutingRateTest smallTest = new RoutingRateTest();
        Stats smallStats = smallTest.runTest(b, q, n);
        smallStats.print();

        RoutingRateTest mediumTest = new RoutingRateTest();
        Stats mediumStats = mediumTest.runTest(b, q, n * 2);
        mediumStats.print();

        RoutingRateTest largeTest = new RoutingRateTest();
        Stats largeStats = largeTest.runTest(b, q, n * 10);
        largeStats.print();


        doFinalSummary(smallStats, mediumStats, largeStats);
    }

    private static void doFinalSummary(Stats smallStats, Stats mediumStats, Stats largeStats) {
        System.err.println();
        System.err.println(".......");
        System.err.println("Final Summary......");
        System.err.println();

        smallStats.print();
        mediumStats.print();
        largeStats.print();
    }

    private Stats runTest(int b, int q, int n) throws Exception {
        Stats stats = new Stats(q,b,n);
        bindings = generate(b, "b.",".*");
        queues = generate(q, "q-", "");

        String x = "x-" + System.currentTimeMillis();

        int bs  = 1000;

        final Connection con = new ConnectionFactory().newConnection("0.0.0.0", 5672);
        Channel channel = con.createChannel();
        channel.exchangeDeclare(1, x, "topic");

        stats.bindingRate = declareAndBindQueues(x, bs, channel);


        ProducerThread producerRef = new ProducerThread(con.createChannel(), x, n);
        Thread producer = new Thread(producerRef);
        producer.start();
        producer.join();

        stats.producerRate = producerRef.rate;

        stats.unbindingRate = deleteQueues(channel);

        channel.close(200, "hasta la vista, baby");
        con.close();
        return stats;
    }

    class Stats {

        int q,b,n;

        Stats (int q, int b, int n) {
            this.q = q;
            this.b = b;
            this.n = n;
        }

        float consumerRate, producerRate, unbindingRate, bindingRate;
        void print() {
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

        ProducerThread(Channel c, String x, int messageCount) {
            this.c = c;
            this.x = x;
            count = messageCount;
            try {
                c.exchangeDeclare(1, x, "topic");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        public void run() {
            final long start = System.currentTimeMillis();
            int n = count;

            doSelect();

            while (n-- > 0) {
                try {

                    send(c, x);

                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            doCommit();

            final long now = System.currentTimeMillis();
            rate = calculateRate("Producer", count, now, start);
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

    private void send(Channel channel, String x) throws IOException {
        byte[] payload = (System.nanoTime() + "-").getBytes();
        Random ran = new Random();
        String b = bindings[ran.nextInt(bindings.length )];
        String r = b.replace("*", System.currentTimeMillis() + "");


        channel.basicPublish(1, x, r, MessageProperties.MINIMAL_BASIC, payload);
        
    }

}
