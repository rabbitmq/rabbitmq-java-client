package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Stack;

/**
 * This tests the scalability of the routing tables in two aspects:
 *
 * 1. The rate of creation and deletion for a fixed level of bindings per queue
 *    accross varying amounts of queues;
 * 2. The rate of publishing n messages to an exchange with a fixed amount of bindings
 *    per queue accross varying amounts of queues.
 *
 *
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

        Parameters params;
        long[] frontNine, backNine;
        boolean flipped = false;
        long start;

        public Measurements(Parameters p, final int magnitude) {
            start = System.nanoTime();
            params = p;
            frontNine = new long[magnitude];
            backNine = new long[magnitude];
        }

        public void flipEggTimer() {
            flipped = true;
            start = System.nanoTime();
        }

        public void addDataPoint(final int i) {
            long now = System.nanoTime();
            long split = now - start;
            if (flipped) {
                backNine[i + 1] = split;
            }
            else {
                frontNine[i] = split; 
            }
        }

        public void analyse(int level) {
            System.out.println("--------------------");
            System.out.println("| Create/Delete");
            System.out.println("| Level = " + level);
            printOutwardStats();
            System.out.println("| ..................");
            printInwardStats();
        }

        private void printInwardStats() {
            long[] tmp = new long[backNine.length + 1];
            System.arraycopy(backNine, 0, tmp, 0, backNine.length);

            for (int i = 0; i < tmp.length - 1; i++) {
                final int amount = pow(params.b, i);
                final long wallclock = tmp[0] - tmp[i + 1];
                float rate = wallclock / (float) amount / 1000;
                printAverage(amount, rate);
            }
        }

        private void printOutwardStats() {
            for (int i = 0; i < frontNine.length; i ++) {
                final int amount = pow(params.b, i);
                final long wallclock = frontNine[i];
                float rate = wallclock  / (float)  amount / 1000;
                printAverage(amount, rate);
            }
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

        // This use of min triangulates the search space so that you
        // if you have max x exponent of 6 and and max y of 6,
        // you don't try to compute 36 points

        loop: for (int i = 0; i < params.y; i++) {

            final int level = pow(params.b, i);
            Stack<String> queues = new Stack<String>();

            Measurements measurements = new Measurements(params, params.x);

            System.out.println("---------------------------------");
            System.out.println("| Routing, n = " + params.n + ", level = " + level);

            // go out
            for (int j = 0; j < params.x; j++) {

                if (i + j > params.combinedLimit()) break loop;

                final int amplitude = pow(params.b, j);

                for (int l = 0; l < amplitude; l++) {
                    AMQP.Queue.DeclareOk ok = channel.queueDeclare(1);
                    queues.push(ok.getQueue());
                    for (int k = 0; k < level  ; k++) {
                        channel.queueBind(1, ok.getQueue(), "amq.direct", randomString());
                    }
                }

                measurements.addDataPoint(j);
                timeRouting(channel, j);
            }


            // flip the egg timer and start to go back
            measurements.flipEggTimer();


            // go back
            int max_exp = params.x - 2;
            int mark = pow(params.b, max_exp);
            while(true) {
                channel.queueDelete(1, queues.pop());
                if (queues.size() == mark) {
                    measurements.addDataPoint(max_exp);
                    if (mark == 1) {
                        channel.queueDelete(1, queues.pop());
                        measurements.addDataPoint(-1);                        
                        break;
                    }
                    else {
                        mark = pow(params.b, --max_exp);
                    }
                }
            }

            measurements.analyse(level);
        }

        channel.close();
        con.close();
    }

    private void timeRouting(Channel channel, int level) throws IOException, InterruptedException {
        // route some messages
        boolean mandatory = true;
        boolean immdediate = true;
        ReturnHandler returnHandler = new ReturnHandler(params);
        channel.setReturnListener(returnHandler);

        for (int n = 0; n < params.n; n ++) {
            String key = randomString();
            channel.basicPublish(1, "amq.direct", key, mandatory, immdediate,
                                 MessageProperties.MINIMAL_BASIC, null);
        }

        // wait for the returns to come back
        int backoff = 10;
        int steps = 0;
        while (returnHandler.returns > 0) {
            Thread.sleep(backoff * steps++);
        }
        returnHandler.printStats(level);
    }

    static class ReturnHandler implements ReturnListener {

        int returns;
        long start, finish;
        Parameters params;

        ReturnHandler(Parameters p) {
            params = p;
            returns = p.n;
            start = System.nanoTime();
        }

        void printStats(int level) {
            long wallclock = finish - start;
            float rate = wallclock  / (float) params.n / 1000;
            // TODO Not quite sure whether printAverage(n, rate) would be more correct
            printAverage(pow(params.b, level), rate);
        }

        public void handleBasicReturn(int replyCode, String replyText,
                                      String exchange, String routingKey,
                                      AMQP.BasicProperties properties, byte[] body) throws IOException {
            returns--;
            if (returns == 0) {
                finish = System.nanoTime();
            }
        }
    }

    private static Parameters setupCLI(String [] args) {
        CLIHelper helper = CLIHelper.defaultHelper();

        helper.addOption(new Option("n", "messages",       true, "number of messages to send"));
        helper.addOption(new Option("r", "routing",       false,
                "whether a routing test should be run instead of a creation test"));
        helper.addOption(new Option("b", "base",          true, "ace of base"));
        helper.addOption(new Option("x", "b-max-exp",       true, "b maximum exponents"));
        helper.addOption(new Option("y", "q-max-exp",       true, "q maximum exponents"));

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

    private String randomString() {
        return System.currentTimeMillis() + "";
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
