package com.rabbitmq.client.test.performance;

import org.apache.commons.cli.*;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;

import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.text.DecimalFormat;

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
            return (x + y);
        }
    }

    private static class Measurements {

        Parameters params;

        Map<Integer, Long> forwardResults = new TreeMap<Integer, Long>();
        TreeMap<Integer, Long> backwardResults = new TreeMap<Integer, Long>();

        int current = 1;
        long start;
        int total;

        public Measurements(Parameters p) {
            params = p;
            total = pow(params.b, params.x);
            reset();
        }

        public void reset() {
            start = System.nanoTime();
            current--;
        }

        public void addDataPoint(final int i, boolean forward) {
            final int j = i + 1;
            if (j == pow(params.b, current)) {
                long now = System.nanoTime();
                if (forward) {
                    forwardResults.put(j, now - start);
                    current++;
                } else {
                    backwardResults.put(current, now - start);
                    current--;
                }
            }
        }

        public void analyse(int level) {
            System.out.println("--------------------");
            System.out.println("| LEVEL = " + level);
            System.out.println("--------------------");
            for (Map.Entry<Integer, Long> entry : forwardResults.entrySet()) {
                final int amount = entry.getKey();
                final long wallclock = entry.getValue();
                float rate = wallclock  / (float)  amount / 1000;
                String rateString = new DecimalFormat("0.00").format(rate);
                System.out.println("| " + amount + " -> " + rateString + " us/op");
            }

            for (Map.Entry<Integer, Long> entry : backwardResults.entrySet()) {
                final long wallclock = entry.getValue();
                Integer higherkey = backwardResults.higherKey(entry.getKey());
                if (null != higherkey) {
                    long exp = entry.getKey();
                    long amount = (params.b - 1) * pow(params.b, (int) exp);
                    Long higherValue = backwardResults.get(higherkey);
                    final long diff = wallclock - higherValue;
                    float rate = diff / (float)  amount / 1000;
                    String rateString = new DecimalFormat("0.00").format(rate);
                    System.out.println("| " + amount + " -> " + rateString + " us/op");
                }
                else {
                    System.out.println("| " + 1 + " -> " + wallclock / 1000 + " us/op");
                }

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
        int x_space = pow(params.b, Math.min(params.x, params.combinedLimit()));

        for (int i = 0; i < params.y; i++) {

            final int level = pow(params.b, i);
            String[] queues = new String[x_space];

            Measurements measurements = new Measurements(params);

            for (int j = 0; j < x_space; j++) {

                AMQP.Queue.DeclareOk ok = channel.queueDeclare(1);
                queues[j] = ok.getQueue();
                for (int k = 0; k < level  ; k++) {
                    channel.queueBind(1, ok.getQueue(), "amq.direct", randomString());
                }
                measurements.addDataPoint(j, true);
            }

            measurements.reset();
            for (int j = x_space - 1; j > -1; j--) {
                channel.queueDelete(1, queues[j]);
                measurements.addDataPoint(j, false);
            }

            measurements.analyse(level);

        }

        channel.close();
        con.close();
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

        params.x =  CLIHelper.getOptionValue(cmd, "x", 3);
        params.y =  CLIHelper.getOptionValue(cmd, "x", 3);

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

}
