package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * This tests the scalability of the routing tables in two aspects:
 *
 * 1. The rate of creation and deletion for a fixed level of bindings
 * per queue accross varying amounts of queues;
 *
 * 2. The rate of publishing n messages to an exchange with a fixed
 * amount of bindings per queue accross varying amounts of queues.
 */
public class ScalabilityTest {

    private static class Parameters {
        String host;
        int port, n, b;
        int x, y;

        int combinedLimit() {
            return (x + y) / 2;
        }
    }

    private static class Measurements {

        private long[] times;
        private long start;

        public Measurements(final int magnitude) {
            times = new long[magnitude];
            start = System.nanoTime();
        }

        public void addDataPoint(final int i) {
            times[i] = System.nanoTime() - start;
        }

        public void analyse(int base) {
            for (int i = 0; i < times.length; i ++) {
                final int amount = pow(base, i);
                final float rate = times[i]  / (float)  amount / 1000;
                printAverage(amount, rate);
            }
        }

        public void adjust() {
            long totalTime = times[0];
            int i;
            for (i = 0; i < times.length - 1; i++) {
                times[i] = totalTime - times[i + 1];
            }
            times[i] = totalTime;
        }

    }

    private Parameters params;

    public ScalabilityTest(Parameters p) {
        params = p;
    }

    public static void main(String[] args) throws Exception {
        Parameters params = setupCLI(args);
        if (params == null) return;

        ScalabilityTest test = new ScalabilityTest(params);
        test.run();
    }


    public void run() throws Exception{
        Connection con = new ConnectionFactory().newConnection(params.host, params.port);
        Channel channel = con.createChannel();

        for (int i = 0; i < params.y; i++) {

            final int level = pow(params.b, i);

            String[] routingKeys =  new String[level];
            for (int p = 0; p < level; p++) {
                routingKeys[p] = UUID.randomUUID().toString();
            }

            Stack<String> queues = new Stack<String>();

            int limit = Math.min(params.x, params.combinedLimit() - i);

            System.out.println("---------------------------------");
            System.out.println("| bindings = " + level + ", messages = " + params.n);

            System.out.println("| Routing");
            Measurements measurements;
            int l = 0;

            // create queues & bindings, time routing
            measurements = new Measurements(limit);
            for (int j = 0; j < limit; j++) {

                final int amplitude = pow(params.b, j);

                for (; l < amplitude; l++) {
                    AMQP.Queue.DeclareOk ok = channel.queueDeclare(1);
                    queues.push(ok.getQueue());
                    for (int k = 0; k < level  ; k++) {
                        channel.queueBind(1, ok.getQueue(), "amq.direct", routingKeys[k]);
                    }
                }

                measurements.addDataPoint(j);

                timeRouting(channel, j, routingKeys);
            }

            System.out.println("| Creating");
            measurements.analyse(params.b);

            // delete queues & bindings
            measurements = new Measurements(limit);
            for (int j = limit - 1; j >= 0; j--) {

                final int amplitude = (j == 0) ? 0 : pow(params.b, j - 1);

                for (; l > amplitude; l--) {
                    channel.queueDelete(1, queues.pop());
                }

                measurements.addDataPoint(j);
            }

            System.out.println("| Deleting");
            measurements.adjust();
            measurements.analyse(params.b);

        }

        channel.close();
        con.close();
    }

    private void timeRouting(Channel channel, int level, String[] routingKeys) throws IOException, InterruptedException {
        // route some messages
        boolean mandatory = true;
        boolean immdediate = true;
        ReturnHandler returnHandler = new ReturnHandler(params);
        channel.setReturnListener(returnHandler);

        final long start = System.nanoTime();

        Random r = new Random();
        int size = routingKeys.length;

        for (int n = 0; n < params.n; n ++) {
            String key = routingKeys[r.nextInt(size)];
            channel.basicPublish(1, "amq.direct", key, mandatory, immdediate,
                                 MessageProperties.MINIMAL_BASIC, null);
        }

        // wait for the returns to come back
        returnHandler.latch.await();

        // Compute the roundtrip time

        final long finish = System.nanoTime();

        final long wallclock = finish - start;
        float rate = wallclock  / (float) params.n / 1000;
        printAverage(pow(params.b, level), rate);
    }

    static class ReturnHandler implements ReturnListener {

        CountDownLatch latch;

        ReturnHandler(Parameters p) {
            latch = new CountDownLatch(p.n);
        }

        public void handleBasicReturn(int replyCode, String replyText,
                                      String exchange, String routingKey,
                                      AMQP.BasicProperties properties, byte[] body) throws IOException {
            latch.countDown();
        }
    }

    private static Parameters setupCLI(String [] args) {
        CLIHelper helper = CLIHelper.defaultHelper();

        helper.addOption(new Option("n", "messages",  true, "number of messages to send"));
        helper.addOption(new Option("b", "base",      true, "base for exponential scaling"));
        helper.addOption(new Option("x", "b-max-exp", true, "maximum per-queue binding count exponent"));
        helper.addOption(new Option("y", "q-max-exp", true, "maximum queue count exponent"));

        CommandLine cmd = helper.parseCommandLine(args);
        if (null == cmd) return null;

        Parameters params = new Parameters();
        params.host =  cmd.getOptionValue("h", "0.0.0.0");
        params.port =  CLIHelper.getOptionValue(cmd, "p", 5672);
        params.n =  CLIHelper.getOptionValue(cmd, "n", 100);
        params.b =  CLIHelper.getOptionValue(cmd, "b", 10);

        params.x =  CLIHelper.getOptionValue(cmd, "x", 4);
        params.y =  CLIHelper.getOptionValue(cmd, "y", 4);

        return params;
    }

    static int pow(int x, int y) {
        int tmp = 1;
        for( int i = 0; i < y; i++ ) tmp *= x;
        return tmp;
    }

    static void printAverage(int amount, float rate) {
        String rateString = new DecimalFormat("0.00").format(rate);
        System.out.println("| " + amount + " -> " + rateString + " us/op");
    }

}
