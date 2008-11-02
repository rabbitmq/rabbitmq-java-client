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

    private abstract static class Measurements {

        protected long[] times;
        private long start;

        public Measurements(final int magnitude) {
            times = new long[magnitude];
            start = System.nanoTime();
        }

        public void addDataPoint(final int i) {
            times[i] = System.nanoTime() - start;
        }

        abstract public float[] analyse(final int base);

        protected static float[] calcOpTimes(final int base, final long[] t) {
            float[] r = new float[t.length];
            for (int i = 0; i < t.length; i ++) {
                final int amount = pow(base, i);
                r[i] = t[i]  / (float)  amount / 1000;
            }

            return r;
        }

    }

    private static class CreationMeasurements extends Measurements {

        public CreationMeasurements(final int magnitude) {
            super(magnitude);
        }

        public float[] analyse(final int base) {
            return calcOpTimes(base, times);
        }

    }

    private static class DeletionMeasurements extends Measurements {

        public DeletionMeasurements(final int magnitude) {
            super(magnitude);
        }

        public float[] analyse(final int base) {
            final long tmp[] = new long[times.length];
            final long totalTime = times[0];
            int i;
            for (i = 0; i < times.length - 1; i++) {
                tmp[i] = totalTime - times[i + 1];
            }
            tmp[i] = totalTime;

            return calcOpTimes(base, tmp);
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

            int l = 0;

            // create queues & bindings, time routing
            Measurements creation = new CreationMeasurements(limit);
            for (int j = 0; j < limit; j++) {

                final int amplitude = pow(params.b, j);

                for (; l < amplitude; l++) {
                    AMQP.Queue.DeclareOk ok = channel.queueDeclare(1);
                    queues.push(ok.getQueue());
                    for (int k = 0; k < level  ; k++) {
                        channel.queueBind(1, ok.getQueue(), "amq.direct", routingKeys[k]);
                    }
                }

                creation.addDataPoint(j);

                float routingTime = timeRouting(channel, j, routingKeys);
                printAverage(pow(params.b, j), routingTime);
            }

            float[] creationTimes = creation.analyse(params.b);
            System.out.println("| Creating");
            printTimes(params.b, creationTimes);

            // delete queues & bindings
            Measurements deletion = new DeletionMeasurements(limit);
            for (int j = limit - 1; j >= 0; j--) {

                final int amplitude = (j == 0) ? 0 : pow(params.b, j - 1);

                for (; l > amplitude; l--) {
                    channel.queueDelete(1, queues.pop());
                }

                deletion.addDataPoint(j);
            }

            float[] deletionTimes = deletion.analyse(params.b);
            System.out.println("| Deleting");
            printTimes(params.b, deletionTimes);
        }

        channel.close();
        con.close();
    }

    private float timeRouting(Channel channel, int level, String[] routingKeys)
        throws IOException, InterruptedException {

        boolean mandatory = true;
        boolean immdediate = true;
        final CountDownLatch latch = new CountDownLatch(params.n);
        channel.setReturnListener(new ReturnListener() {
                public void handleBasicReturn(int replyCode, String replyText,
                                              String exchange, String routingKey,
                                              AMQP.BasicProperties properties, byte[] body) throws IOException {
                    latch.countDown();
                }
            });

        final long start = System.nanoTime();

        // route some messages
        Random r = new Random();
        int size = routingKeys.length;
        for (int n = 0; n < params.n; n ++) {
            String key = routingKeys[r.nextInt(size)];
            channel.basicPublish(1, "amq.direct", key, mandatory, immdediate,
                                 MessageProperties.MINIMAL_BASIC, null);
        }

        // wait for the returns to come back
        latch.await();

        // Compute the roundtrip time
        final long finish = System.nanoTime();
        final long wallclock = finish - start;
        return wallclock  / (float) params.n / 1000;
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

    static void printTimes(int base, float[] times) {
        for (int i = 0; i < times.length; i ++) {
            final int x = pow(base, i);
            printAverage(x, times[i]);
        }
    }

    static void printAverage(int amount, float rate) {
        String rateString = new DecimalFormat("0.00").format(rate);
        System.out.println("| " + amount + " -> " + rateString + " us/op");
    }

}
