package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;

import java.io.*;
import java.util.Random;

import org.apache.commons.cli.*;

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
public class BaseRoutingRateTest {

    /** IP of the broker */
    private static String HOST;
    /** Port that the broker listens on */
    private static int PORT;

    protected String[] bindings, queues;

    public static void main(String[] args) throws Exception {

        Options options = getOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        }
        catch (ParseException e) {
            System.err.println("Parsing failed. Reason: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("BaseRoutingRateTest", options);
            return;
        }

        HOST = cmd.getOptionValue("h", "0.0.0.0");
        PORT = Integer.parseInt(cmd.getOptionValue("p", "5672"));

        final int rateRedux = Integer.parseInt(cmd.getOptionValue("d", "10000"));
        final int interval = Integer.parseInt(cmd.getOptionValue("i", "50"));


        final int b = Integer.parseInt(cmd.getOptionValue("b", "250"));
        final int q = Integer.parseInt(cmd.getOptionValue("q", "250"));
        final int n = Integer.parseInt(cmd.getOptionValue("n", "1000"));

        final int i = Integer.parseInt(cmd.getOptionValue("s", "10"));

        final boolean topic = Boolean.parseBoolean(cmd.getOptionValue("t", "false"));
        final boolean latency = Boolean.parseBoolean(cmd.getOptionValue("l", "false"));

        Parameters params = new Parameters(b, q, n, rateRedux, interval, topic, latency, i);

        BaseRoutingRateTest test = new BaseRoutingRateTest();
        test.runStrategy(params);
        
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(new Option("h", "host",           true, "broker host"));
        options.addOption(new Option("p", "port",           true, "broker port"));
        options.addOption(new Option("d", "rate-redux",     true, "rate redux per iteration"));
        options.addOption(new Option("i", "interval",       true, "backoff interval"));
        options.addOption(new Option("b", "bindings",       true, "number of bindings per queue"));
        options.addOption(new Option("q", "queues",         true, "number of queues to create"));
        options.addOption(new Option("n", "messages",       true, "number of messages to send"));
        options.addOption(new Option("s", "iterations",     true, "number of iterations for rate limited"));
        options.addOption(new Option("l", "latency",        false,
                          "whether a latency test should be run instead of a thorughput test"));
        options.addOption(new Option("t", "topic",          false,
                          "whether a topic exchange should used instead of a direct exchange"));
        return options;
    }


    public void runStrategy(Parameters p) throws Exception {

        final int iterations = (p.latency) ? p.iterations : 1;

        final int small = 1;
        final int medium = small * 5;
        final int large = small * 10;

        for (int i = 0 ; i < iterations ; i++) {
            int amplification = (p.latency) ? (iterations - i) * p.rateRedux : 1;
            Parameters smallStats = calibrateAndRunTest(p, small, amplification);            
            Parameters mediumStats = calibrateAndRunTest(p, medium, amplification);
            Parameters largeStats = calibrateAndRunTest(p, large, amplification);
            doFinalSummary(smallStats, mediumStats, largeStats);
        }

    }

    private Parameters calibrateAndRunTest(Parameters p, int magnification, int amplification) throws Exception {
        BaseRoutingRateTest test = new BaseRoutingRateTest();
        Parameters parameters = p.magnify(magnification).amplify(amplification);
        return test.runTest(parameters);
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

    private Parameters runTest(Parameters parameters) throws Exception {

        String postfix = (parameters.topic) ? ".*" : "";
        String type = (parameters.topic) ? "topic" : "direct";

        bindings = generate(parameters.b, "b.", postfix);
        queues = generate(parameters.q, "q-", "");

        String x = "x-" + System.currentTimeMillis();

        int bs  = 1000;

        final Connection con = new ConnectionFactory().newConnection(HOST, PORT);
        Channel channel = con.createChannel();
        channel.exchangeDeclare(1, x, type);

        parameters.dashboard.bindingRate = declareAndBindQueues(x, bs, channel);


        ProducerThread producerRef = new ProducerThread(con.createChannel(), x, parameters);
        Thread producer = new Thread(producerRef);

        if (parameters.latency) {
            ConsumerThread consumerRef = new ConsumerThread(con.createChannel(), producer, parameters);
            Thread consumer = new Thread(consumerRef);
            consumer.start();
            consumer.join();
            producer.join();
            parameters.dashboard.consumerRate = consumerRef.rate;
        }

        else {
            producer.start();
            producer.join();
        }

        parameters.dashboard.producerRate = producerRef.rate;
        parameters.dashboard.unbindingRate = deleteQueues(channel);

        channel.close(200, "hasta la vista, baby");
        con.close();

        return parameters;
    }

    /**
     *  This is a struct that encapsulates all of the various parameters
     *  needed to run this test. 
     */
    private static class Parameters implements Cloneable {

        // ---------------------------
        // These are static parameters
        // ---------------------------

        /** This is the amount of queues to create */
        final int q;
        /** This is the amount of bindings per queue */
        final int b;
        /** This is the number of messages to send to the exchange */
        int n;

        final int rateRedux;

        final int interval;

        final int iterations;

        boolean topic, latency;

        // ---------------------------
        // Run time instrumentation
        // ---------------------------

        private static class Dashboard {
            /** The current rate limit */
            int rateLimit;
            float consumerRate, producerRate, unbindingRate, bindingRate;
        }

        Dashboard dashboard = new Dashboard();

        Parameters(int q, int b, int n, int rateRedux, int interval, boolean topic, boolean latency, int iterations) {
            this.q = q;
            this.b = b;
            this.n = n;
            this.rateRedux = rateRedux;
            this.interval = interval;
            this.topic = topic;
            this.latency = latency;
            this.iterations = iterations;
        }



        /**
         * This increases the value of the number of messages by the factor that you pass in.
         * This creates a copy of the original object reference, so this is effectively
         * a non-destructive assignment.
         */
        Parameters magnify(int factor) {
            Parameters copy = copy();
            copy.n *= factor;
            return copy;
        }

        /**
         * This amplifies the rate limit by the factor that you pass in.
         * This is useful for loops that pass in a new factor on each iteration.
         */
        Parameters amplify(int factor) {
            Parameters copy = copy();
            copy.dashboard.rateLimit = factor * rateRedux;
            return copy;
        }

        /**
         * Convenience method to clone this instance, swallows a checked exception :-)
         */
        Parameters copy() {
            try {
                 return (Parameters) clone();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void printStats() {
            System.err.println("----------------");
            System.err.println("SUMMARY (q = " + q + ", b = " + b + ", n = " + n + ")");
            System.err.println("Consumer -> " + dashboard.consumerRate);
            System.err.println("Producer -> " + dashboard.producerRate);
            System.err.println("Creation -> " + dashboard.bindingRate);
            System.err.println("Nuking -> " +   dashboard.unbindingRate);
            System.err.println("Rate Limit -> " + dashboard.rateLimit);
            System.err.println("Rate Redux -> " + rateRedux);
            System.err.println("Interval -> " + interval);

            System.err.println("----------------");
        }
    }

    private float declareAndBindQueues(String x, int bs, Channel channel) throws IOException {
        //System.err.println("Creating queues ....... ");
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
        //System.err.println(who + " : Rate = " + size  / diff);
        return rate;
    }

    private float deleteQueues(Channel channel) throws IOException {
        //System.err.println("Deleting queues ....... ");
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

        Thread producer;
        float rate;

        Channel c;

        Parameters params;

        ConsumerThread(Channel channel, Thread t, Parameters p) {
            super(channel);
            producer = t;
            params = p;
            c = channel;
        }

        public void run() {

            // Subscribe to each queue

            String[] tags = new String[queues.length];
            int j = 0;

            for (String queue : queues) {
                try {
                    tags[j++] = getChannel().basicConsume(1,queue,this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // Start the producer thread now
            producer.start();

            final long start = System.currentTimeMillis();

            int n = params.n;
            long acc = 0;
            while (n-- > 0) {
                try {
                    Delivery delivery = nextDelivery();
                    long now = System.currentTimeMillis();
                    if (null != delivery) {
                        ByteArrayInputStream is = new ByteArrayInputStream(delivery.getBody());
                        DataInputStream d = new DataInputStream(is);
                        try {
                            int sequenceNumber = d.readInt();
                            long then = d.readLong();
                            acc += (now - then);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }



            final long now = System.currentTimeMillis();
            if (params.latency){

                rate = acc / params.n;

            }
            else {
                rate = calculateRate("Consumer", params.n, now, start);
            }

            // Unsubscribe to each queue

            for (String tag : tags) {
                try {
                    getChannel().basicCancel(tag);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                c.close(200, "see ya");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class ProducerThread implements Runnable {

        float rate;

        private Channel c;
        String x;

        long lastStatsTime;

        int sequenceNumber = 0;

        int n;

        Parameters params;

        ProducerThread(Channel c, String x, Parameters p) {
            this.c = c;
            this.x = x;
            this.params = p;

            try {
                c.exchangeDeclare(1, x, (p.topic) ? "topic" : "direct");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        public void run() {

            // Squirrel away the starting value for the number of messages to send
            n = params.n;

            long start = lastStatsTime = System.currentTimeMillis();

            if (!params.latency) doSelect();

            while (n-- > 0) {
                try {

                    send(c, x, params.topic);
                    if (params.latency) {
                        delay(System.currentTimeMillis());
                    }

                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            if (!params.latency) doCommit();

            try {
                c.close(200, "see ya");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            final long nownow = System.currentTimeMillis();
            rate = (params.latency) ? -1 : calculateRate("Producer", params.n, nownow, start);
        }

        private void delay(final long now) throws InterruptedException {

            final long elapsed = now - lastStatsTime;
            //example: rateLimit is 5000 msg/s,
            //10 ms have elapsed, we have sent 200 messages
            //the 200 msgs we have actually sent should have taken us
            //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
            final long pause = params.dashboard.rateLimit == 0 ?
                0 : ( (params.n - n)  * 1000L / params.dashboard.rateLimit - elapsed);
            if (pause > 0) {
                Thread.sleep(pause);
            }
            if (elapsed > params.interval) {
//                System.out.println("sending rate: " +
//                                   ( (count - n) * 1000 / elapsed) +
//                                   " msg/s");
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

        private void send(Channel ch, String x, boolean topic) throws IOException {
            
            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(acc);
            d.writeInt(sequenceNumber++);
            d.writeLong(System.currentTimeMillis());
            d.flush();
            acc.flush();
            byte[] payload = acc.toByteArray();

            Random ran = new Random();
            String b = bindings[ran.nextInt(bindings.length)];
            String r = (topic) ? b.replace("*", System.currentTimeMillis() + "") : b;

            ch.basicPublish(1, x, r, MessageProperties.MINIMAL_BASIC, payload);

        }
    }



}
