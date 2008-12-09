//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
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
        int port;
        int messageCount;
        int base, maxQueueExp, maxBindingExp, maxExp;
        String filePrefix;

    }

    private abstract static class Measurements {

        protected long[] times;
        private long start;

        public Measurements(final int count) {
            times = new long[count];
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

        public CreationMeasurements(final int count) {
            super(count);
        }

        public float[] analyse(final int base) {
            return calcOpTimes(base, times);
        }

    }

    private static class DeletionMeasurements extends Measurements {

        public DeletionMeasurements(final int count) {
            super(count);
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

    private static class Results {

        float[][] creationTimes;
        float[][] deletionTimes;
        float[][] routingTimes;

        public Results(final int y) {
            creationTimes = new float[y][];
            deletionTimes = new float[y][];
            routingTimes = new float[y][];
        }

        public void print(final int base, final String prefix)
            throws IOException {

            PrintStream s;
            s = open(prefix, "creation");
            print(s, base, creationTimes);
            s.close();
            s = open(prefix, "deletion");
            print(s, base, deletionTimes);
            s.close(); 
            s = open(prefix, "routing");
            print(s, base, transpose(routingTimes));
            s.close();
        }

        private static PrintStream open(final String prefix,
                                        final String suffix)
            throws IOException {

            return new PrintStream(new FileOutputStream(prefix + suffix +
                                                        ".dat"));
        }

        private static void print(final PrintStream s, final int base,
                                  final float[][] times) {
            for (int y = 0; y < times.length; y++) {
                s.println("# level " + pow(base, y));
                for (int x = 0; x < times[y].length; x++) {
                    s.println(pow(base, x) + " " + format.format(times[y][x]));
                }
                s.println();
                s.println();
            }
        }

        private float[][] transpose(float[][] m) {
            Vector<Vector<Float>> tmp = new Vector<Vector<Float>>();
            for (int i = 0; i < m[0].length; i++) {
                tmp.addElement(new Vector<Float>());
            }
            for (int i = 0; i < m.length; i++) {
                for (int j = 0; j < m[i].length; j++) {
                    Vector<Float> v = tmp.get(j);
                    v.addElement(m[i][j]);
                }
            }
            float[][] r = new float[tmp.size()][];
            for (int i = 0; i < tmp.size(); i++) {
                Vector<Float> v = tmp.get(i);
                float[] vr = new float[v.size()];
                for (int j = 0; j < v.size(); j++) {
                    vr[j] = v.get(j);
                }
                r[i] = vr;
            }
            return r;
        }
    }

    private static NumberFormat format = new DecimalFormat("0.00");

    private final Parameters params;

    public ScalabilityTest(Parameters p) {
        params = p;
    }

    public static void main(String[] args) throws Exception {
        Parameters params = parseArgs(args);
        if (params == null) return;

        ScalabilityTest test = new ScalabilityTest(params);
        Results r = test.run();
        if (params.filePrefix != null)
            r.print(params.base, params.filePrefix);
    }


    public Results run() throws Exception{
        Connection con = new ConnectionFactory().newConnection(params.host, params.port);
        Channel channel = con.createChannel();

        Results r = new Results(params.maxBindingExp);

        for (int y = 0; y < params.maxBindingExp; y++) {

            final int maxBindings = pow(params.base, y);

            String[] routingKeys =  new String[maxBindings];
            for (int b = 0; b < maxBindings; b++) {
                routingKeys[b] = UUID.randomUUID().toString();
            }

            Stack<String> queues = new Stack<String>();

            int maxQueueExp = Math.min(params.maxQueueExp, params.maxExp - y);

            System.out.println("---------------------------------");
            System.out.println("| bindings = " + maxBindings + ", messages = " + params.messageCount);

            System.out.println("| Routing");

            int q = 0;

            // create queues & bindings, time routing
            Measurements creation = new CreationMeasurements(maxQueueExp);
            float routingTimes[] = new float[maxQueueExp];
            for (int x = 0; x < maxQueueExp; x++) {

                final int maxQueues = pow(params.base, x);

                for (; q < maxQueues; q++) {
                    AMQP.Queue.DeclareOk ok = channel.queueDeclare();
                    queues.push(ok.getQueue());
                    for (int b = 0; b < maxBindings; b++) {
                        channel.queueBind(ok.getQueue(), "amq.direct", routingKeys[b]);
                    }
                }

                creation.addDataPoint(x);

                float routingTime = timeRouting(channel, routingKeys);
                routingTimes[x] = routingTime;
                printTime(params.base, x, routingTime);
            }

            r.routingTimes[y] = routingTimes;
            float[] creationTimes = creation.analyse(params.base);
            r.creationTimes[y] = creationTimes;
            System.out.println("| Creating");
            printTimes(params.base, creationTimes);

            // delete queues & bindings
            Measurements deletion = new DeletionMeasurements(maxQueueExp);
            for (int x = maxQueueExp - 1; x >= 0; x--) {

                final int maxQueues = (x == 0) ? 0 : pow(params.base, x - 1);

                for (; q > maxQueues; q--) {
                    channel.queueDelete(queues.pop());
                }

                deletion.addDataPoint(x);
            }

            float[] deletionTimes = deletion.analyse(params.base);
            r.deletionTimes[y] = deletionTimes;
            System.out.println("| Deleting");
            printTimes(params.base, deletionTimes);
        }

        channel.close();
        con.close();

        return r;
    }

    private float timeRouting(Channel channel, String[] routingKeys)
        throws IOException, InterruptedException {

        boolean mandatory = true;
        boolean immdediate = true;
        final CountDownLatch latch = new CountDownLatch(params.messageCount);
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
        for (int n = 0; n < params.messageCount; n ++) {
            String key = routingKeys[r.nextInt(size)];
            channel.basicPublish("amq.direct", key, mandatory, immdediate,
                                 MessageProperties.MINIMAL_BASIC, null);
        }

        // wait for the returns to come back
        latch.await();

        // Compute the roundtrip time
        final long finish = System.nanoTime();
        final long wallclock = finish - start;
        return wallclock  / (float) params.messageCount / 1000;
    }

    private static Parameters parseArgs(String [] args) {
        CLIHelper helper = CLIHelper.defaultHelper();

        helper.addOption(new Option("n", "messages",  true, "number of messages to send"));
        helper.addOption(new Option("b", "base",      true, "base for exponential scaling"));
        helper.addOption(new Option("x", "q-max-exp", true, "maximum queue count exponent"));
        helper.addOption(new Option("y", "b-max-exp", true, "maximum per-queue binding count exponent"));
        helper.addOption(new Option("c", "c-max-exp", true, "combined maximum exponent"));
        helper.addOption(new Option("f", "file",      true, "result files prefix; defaults to no file output"));

        CommandLine cmd = helper.parseCommandLine(args);
        if (null == cmd) return null;

        Parameters params = new Parameters();
        params.host          = cmd.getOptionValue("h", "0.0.0.0");
        params.port          = CLIHelper.getOptionValue(cmd, "p", 5672);
        params.messageCount  = CLIHelper.getOptionValue(cmd, "n", 100);
        params.base          = CLIHelper.getOptionValue(cmd, "b", 10);
        params.maxQueueExp   = CLIHelper.getOptionValue(cmd, "x", 4);
        params.maxBindingExp = CLIHelper.getOptionValue(cmd, "y", 4);
        params.maxExp        = CLIHelper.getOptionValue(cmd, "c", Math.max(params.maxQueueExp, params.maxBindingExp));
        params.filePrefix    = cmd.getOptionValue("f", null);

        return params;
    }

    private static int pow(int x, int y) {
        int r = 1;
        for( int i = 0; i < y; i++ ) r *= x;
        return r;
    }

    private static void printTimes(int base, float[] times) {
        for (int i = 0; i < times.length; i ++) {
            printTime(base, i, times[i]);
        }
    }

    private static void printTime(int base, int exp, float v) {
        System.out.println("| " + pow(base, exp) +
                           " -> " + format.format(v) + " us/op");
    }

}
