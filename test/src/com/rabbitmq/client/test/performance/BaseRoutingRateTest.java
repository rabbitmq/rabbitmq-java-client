package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;

import java.io.*;
import java.util.Random;
import java.text.DecimalFormat;

import org.apache.commons.cli.*;

/**
 * This class contains two types of tests - one for measuring routing throughput and
 * on for measuring latency.
 *
 * Each test should be timed for different values for q, b and n.
 *
 * In both test cases, a pool of random queue names and binding keys is created.
 *
 * Each queue is bound to with each binding to the default exchange, hence producing
 * b * q routes.
 *
 * The throughput test has 4 phases:
 *
 * 0. Declare all of the queues in the pool and bind each one to each routing key in the pool of keys;
 * 1. Start a producer thread;
 * 2. Start a transaction, send n messages and the commit this;
 * 3. Delete all of the queues, thus unbinding everything
 *
 * The latency test has 6 phases:
 *
 * 0. Declare all of the queues in the pool and bind each one to each routing key in the pool of keys;
 * 1. Start a consumer thread, set up q queues with b bindings and subscribe to them;
 * 2. Run a producer thread and send n messages;
 * 3. Consumer thread should receive n messages;
 * 4. Unsubscribe from all of the queues;
 * 5. Delete all of the queues, thus unbinding everything;
 *
 * If you want to find out how to calibrate this test, add --help as a command line argument
 * to see what options are available.
 *
 * Your mileage may vary :-) 
 *
 */
public class BaseRoutingRateTest {

    /** IP of the broker */
    private static String HOST;
    /** Port that the broker listens on */
    private static int PORT;
    /** Pools of randomly created queues and bindings */
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
            printHelp(options);
            return;
        }

        if (cmd.hasOption("help")) {
            printHelp(options);
            return;
        }

        HOST = cmd.getOptionValue("h", "0.0.0.0");
        PORT = Integer.parseInt(cmd.getOptionValue("p", "5672"));

        final int rateRedux = Integer.parseInt(cmd.getOptionValue("d", "1000"));
        final int interval = Integer.parseInt(cmd.getOptionValue("i", "50"));


        final int b = Integer.parseInt(cmd.getOptionValue("b", "250"));
        final int q = Integer.parseInt(cmd.getOptionValue("q", "250"));
        final int n = Integer.parseInt(cmd.getOptionValue("n", "1000"));

        final int i = Integer.parseInt(cmd.getOptionValue("s", "10"));

        final boolean topic = cmd.hasOption("t");
        final boolean latency = cmd.hasOption("l");

        Parameters params = new Parameters(b, q, n, rateRedux, interval, topic, latency, i);

        BaseRoutingRateTest test = new BaseRoutingRateTest();
        test.runStrategy(params);
        
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("BaseRoutingRateTest", options);
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(new Option( "help", "print this message" ));
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
            int amplification = (p.latency) ? (iterations - i) : 1;
            calibrateAndRunTest(p, small, amplification).printStats();
            calibrateAndRunTest(p, medium, amplification).printStats();
            calibrateAndRunTest(p, large, amplification).printStats();
        }

    }

    private Parameters calibrateAndRunTest(Parameters p, int magnification, int amplification) throws Exception {
        BaseRoutingRateTest test = new BaseRoutingRateTest();
        Parameters parameters = p.magnify(magnification).amplify(amplification);
        return test.runTest(parameters);
    }

    /**
     * This runs the main test based on the parameters passed in:
     *
     *  1. Create a pool of random queue names and a pool of random binding keys;
     *  2. Connect to the broker and start a channel;
     *  3. Iterate through the pool of queues and declare them;
     *  4. Iterate through the bindings by binding each queue with b separate bindings
     *     to the default exchange;
     *  5. Start a producer thread, tell it to send n messages;
     *  6. Start a consumer consumer and tell it to receive n messages;
     *  7. Wait for both the producer and consumer to finish, then print the results;
     *  8. Close the channel and connection.
     *
     * The various parameters have these effects:
     *
     * - If the topic flag is set, then perform topic routing - this means that the routing
     *   key will end with a wilcard;
     * - If the latency flag is not set, then just start a producer;
     * - If the latency flag is set, then have the consumer thread start the producer
     *   thread after it has subscribed itself to all of the queues.
     *
     */
    private Parameters runTest(Parameters parameters) throws Exception {

        String postfix = (parameters.topic) ? ".*" : "";
        String x = (parameters.topic) ? "amq.topic" : "amq.direct";

        bindings = generate(parameters.b, "b.", postfix);
        queues = generate(parameters.q, "q-", "");

        final Connection con = new ConnectionFactory().newConnection(HOST, PORT);
        Channel channel = con.createChannel();

        parameters.dashboard.bindingRate = declareAndBindQueues(channel, x);


        ProducerThread producerRef = new ProducerThread(con.createChannel(), parameters);
        Thread producer = new Thread(producerRef);

        ConsumerThread consumerRef = new ConsumerThread(con.createChannel(), producer, parameters);
        Thread consumer = new Thread(consumerRef);

        consumer.start();

        if (!parameters.latency) {
            producer.start();
        }

        consumer.join();
        producer.join();
        
        parameters.dashboard.consumerRate = consumerRef.rate;
        parameters.dashboard.consumerLatency = consumerRef.latency;
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
            float consumerRate, consumerLatency, producerRate, unbindingRate, bindingRate;
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
            System.out.println("--------------------------------------------");
            System.out.println("Summary\t\t\t{q = " + q + " b = " + b + " n = " + n + "}");
            System.out.println("--------------------------------------------");
            if (dashboard.consumerRate > 0) {
                System.out.println("Consumption\t\t" + new DecimalFormat("#").format(dashboard.consumerRate) + " \tmsg/s");
            }
            if (dashboard.consumerLatency > 0) {
                System.out.println("Latency\t\t\t" + new DecimalFormat("#").format(dashboard.consumerLatency) + " \t\tms/msg");
            }
            if (dashboard.producerRate > 0) {
                System.out.println("Production\t\t" + new DecimalFormat("#").format(dashboard.producerRate) + "\tmsg/s");
            }
            System.out.println("Creation\t\t" + new DecimalFormat("#").format(dashboard.bindingRate) + "\tbinds/s");
            System.out.println("Deletion\t\t" +   new DecimalFormat("#").format(dashboard.unbindingRate) + "\tunbinds/s");

            if (latency) {
                System.out.println("Rate Limit\t\t" + dashboard.rateLimit + " \tmsg/s");
                System.out.println("Rate Redux\t\t" + rateRedux + "\t\titems");
                System.out.println("Interval\t\t" + interval + "\t\tms");
            }
        }
    }

    /**
     * This declares and queues defined in the pool of random queue names.
     * Then it runs through the pool of random bindings and binds them to the default exchange.
     */
    private float declareAndBindQueues(Channel channel, String x) throws IOException {
        final long start = System.currentTimeMillis();
        for (String queue : queues) {
            channel.queueDeclare(1, queue);
            for (String binding : bindings) {
                channel.queueBind(1, queue, x, binding);
            }
        }
        final long now = System.currentTimeMillis();
        return calculateRate(bindings.length * queues.length, now, start);
    }

    private float calculateRate(int size, long now, long then) {
        float diff = (float)(now - then) / 1000;
        return size  / diff;
    }

    private float deleteQueues(Channel channel) throws IOException {
        long start = System.currentTimeMillis();
        for (String queue : queues) {
            channel.queueDelete(1, queue);
        }
        long now = System.currentTimeMillis();
        return calculateRate(bindings.length * queues.length, now, start);
    }

    /**
     * This generates a pool of random strings, each pre- and postfixed
     * with a specific string that is specified with extra parameters.
     */
    private String[] generate(int z, String prefix, String postfix) {
        String[] s =  new String[z];
        Random r = new Random();
        for (int i = 0; i < z; i++) {
            s[i] = prefix + r.nextLong() + postfix;
        }
        return s;
    }

    /**
     * This consumer thread is used only when testing for latency.
     *
     * This thread subscribes to all of the queues defined in the random pool
     * of queue names.
     *
     * After it has done that it spawns a producer thread in order to start
     * sending messages to the exchange.
     *
     * This thread will exit when it has drained n messages in total from all
     * of the queues.
     *
     * The rate calculations it makes are based on the assumption the sender and
     * itself are running in the same address space and hence there can be no
     * clock drift.
     */
    class ConsumerThread extends QueueingConsumer implements Runnable {

        Thread producer;
        float rate = 0;
        float latency = 0;
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

            // Start the producer thread now and then begin to drain the queues

            if (params.latency) {
                producer.start();
            }

            final long start = System.currentTimeMillis();

            int n = params.n;
            long cumulativeLatency = 0;

            // This is main part of draining all of the queues

            while (n-- > 0) {
                try {
                    Delivery delivery = nextDelivery();
                    long now = System.currentTimeMillis();
                    if (null != delivery) {
                        ByteArrayInputStream is = new ByteArrayInputStream(delivery.getBody());
                        DataInputStream d = new DataInputStream(is);
                        try {
                            long then = d.readLong();
                            cumulativeLatency += (now - then);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            final long now = System.currentTimeMillis();

            // Now that all of the queues have been drained,
            // the calculations can be performed.

            if (params.latency){

                latency = cumulativeLatency / params.n;

            }
            else {
                rate = calculateRate(params.n, now, start);
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

    /**
     * The producer thread has two modes of operation, depending on whether
     * latency or throughput is being measured.
     *
     * If throughput is being measured, then the producer will batch each message
     * then it sends into a transaction. This is ensure that the actual time spent
     * in the routing pipeline is measured rather than just the time it took to put
     * the message onto the wire. The rate measurement is simply the time it took
     * to start a TX, send n messages and receive the commit acknowldegement from the
     * broker.
     *
     * If latency is the subject of investigation, no transactions are started, rather
     * the onus of measurment is on the consumer thread not on the producer.
     */
    class ProducerThread implements Runnable {

        float rate;

        private Channel c;

        long lastStatsTime;

        int n;

        Parameters params;

        ProducerThread(Channel c, Parameters p) {
            this.c = c;
            this.params = p;
        }

        public void run() {

            // Squirrel away the starting value for the number of messages to send
            n = params.n;

            long start = lastStatsTime = System.currentTimeMillis();

            doSelect();

            while (n-- > 0) {
                send(c, params.topic);
                delay(System.currentTimeMillis());
            }

            doCommit();

            try {
                c.close(200, "see ya");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            final long nownow = System.currentTimeMillis();
            rate = (params.latency) ? -1 : calculateRate(params.n, nownow, start);
        }

        /**
         * This is Matthias' special sauce - ask him what it does :-)
         */
        private void delay(final long now) {
            if (params.latency) {
                final long elapsed = now - lastStatsTime;
                //example: rateLimit is 5000 msg/s,
                //10 ms have elapsed, we have sent 200 messages
                //the 200 msgs we have actually sent should have taken us
                //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
                final long pause = params.dashboard.rateLimit == 0 ?
                    0 : ( (params.n - n)  * 1000L / params.dashboard.rateLimit - elapsed);
                if (pause > 0) {
                    try {
                        Thread.sleep(pause);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (elapsed > params.interval) {
                    lastStatsTime = now;
                }
            }
        }

        /**
         * Selectively end a transaction for throughput measurement
         */
        private void doCommit() {
            if (!params.latency) {
                try {
                    c.txCommit();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        /**
         * Selectively start a transaction for throughput measurement
         */
        private void doSelect() {
            if (!params.latency) {
                try {
                    // Who invented checked exceptions?
                    c.txSelect();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        /**
         * Sends a timestamp with a randomly selected element from
         * the pool of random routing keys.
         *
         * If sending to a topic exchange, then change the * wildcard to some
         * random concrete value, so that the broker has to match on this structured
         * key (I guess this could be used to test topic route caching if we ever
         * decided to implement this).
         */
        private void send(Channel ch, boolean topic) {
            try {
                ByteArrayOutputStream acc = new ByteArrayOutputStream();
                DataOutputStream d = new DataOutputStream(acc);
                d.writeLong(System.currentTimeMillis());
                d.flush();
                acc.flush();
                byte[] payload = acc.toByteArray();
                Random ran = new Random();
                String b = bindings[ran.nextInt(bindings.length)];
                String r = (topic) ? b.replace("*", System.currentTimeMillis() + "") : b;
                String x = (topic) ? "amq.topic" : "amq.direct";
                ch.basicPublish(1, x, r, MessageProperties.MINIMAL_BASIC, payload);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }



}
