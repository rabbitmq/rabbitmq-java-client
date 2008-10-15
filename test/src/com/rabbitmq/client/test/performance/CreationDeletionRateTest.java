package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;

import java.util.Random;

import org.apache.commons.cli.*;

/**
 * This is a performance test for testing the rate at which queues and bindings
 * are created and deleted in a single thread of execution.
 *
 * The statistics produced by this test are calculated in the following fashion:
 *
 *      (number of queues * number of bindings per queue) / elapsed time
 *
 * The results of the test consist of two components:
 *
 * - The rate at which q random queues can be created and bound to the default
 *   exchange with b random bindings per queue;
 *
 * - The rate at which q random queues with b bindings per queue can be deleted.
 *  
 */
public class CreationDeletionRateTest {

    private static Random rand = new Random();

    public static void main(String[] args) throws Exception {

        Options options = getOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cmd = parser.parse(options, args);

        final String host = cmd.getOptionValue("h", "0.0.0.0");
        final int port = Integer.parseInt(cmd.getOptionValue("p", "5672"));
        final int b = Integer.parseInt(cmd.getOptionValue("b", "250"));
        final int q = Integer.parseInt(cmd.getOptionValue("q", "250"));

        final Connection con = new ConnectionFactory().newConnection(host, port);
        Channel channel = con.createChannel();

        String[] queues = new String[q];

        int k = 0;

        final long start = System.currentTimeMillis();

        for (int i = 0; i < q; i++) {
            String s = newRandomName();
            channel.queueDeclare(1, s);
            queues[k++] = s;
            for (int j = 0; j < b; j++) {
                channel.queueBind(1, s, "amq.direct", newRandomName());
            }
        }

        final long split = System.currentTimeMillis();

        printStats("Creation", b, q, start, split);

        for (String qN : queues) {
            channel.queueDelete(1, qN);
        }

        final long stop = System.currentTimeMillis();

        printStats("Deletion", b, q, split, stop);

        channel.close(200, "foo");
        con.close();
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(new Option("h", "host",      true, "broker host"));
        options.addOption(new Option("p", "port",      true, "broker port"));
        options.addOption(new Option("b", "bindings",  true, "number of bindings per queue"));
        options.addOption(new Option("q", "queues",    true, "number of queues to create"));
        return options;
    }

    private static void printStats(String label, int b, int q, long start, long split) {
        System.out.println(label + " rate: "
                           + (float) (b * q) / (split - start) * 1000
                           + " operations / sec");
    }

    private static String newRandomName() {
        return rand.nextInt() + "-" + System.currentTimeMillis() + "";
    }

}
