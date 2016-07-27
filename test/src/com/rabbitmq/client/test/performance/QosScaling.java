// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.


package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class QosScaling {

    protected static class Parameters {
        final String host;
        final int port;
        final int messageCount;
        final int queueCount;
        final int emptyCount;

        public static CommandLine parseCommandLine(String[] args) {
            CLIHelper helper = CLIHelper.defaultHelper();
            helper.addOption(new Option("n", "messages", true, "number of messages to send"));
            helper.addOption(new Option("q", "queues",   true, "number of queues to route messages to"));
            helper.addOption(new Option("e", "empty",    true, "number of queues to leave empty"));
            return helper.parseCommandLine(args);
        }

        public Parameters(CommandLine cmd) {
            host         = cmd.getOptionValue("h", "localhost");
            port         = CLIHelper.getOptionValue(cmd, "p", AMQP.PROTOCOL.PORT);
            messageCount = CLIHelper.getOptionValue(cmd, "n", 2000);
            queueCount   = CLIHelper.getOptionValue(cmd, "q", 100);
            emptyCount   = CLIHelper.getOptionValue(cmd, "e", 0);
        }

        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("host="      + host);
            b.append(",port="     + port);
            b.append(",messages=" + messageCount);
            b.append(",queues="   + queueCount);
            b.append(",empty="    + emptyCount);
            return b.toString();
        }

    }

    protected final Parameters params;
    protected final ConnectionFactory connectionFactory =
        new ConnectionFactory();
    protected Connection connection;
    protected Channel channel;

    public QosScaling(Parameters p) {
        params = p;
    }

    protected List<String> consume(QueueingConsumer c) throws IOException {
        for (int i = 0; i < params.emptyCount; i++) {
            String queue = channel.queueDeclare().getQueue();
            channel.basicConsume(queue, false, c);
        }
        List<String> queues = new ArrayList<String>();
        for (int i = 0; i < params.queueCount; i++) {
            String queue = channel.queueDeclare().getQueue();
            channel.basicConsume(queue, false, c);
            queues.add(queue);
        }
        return queues;
    }

    protected void publish(List<String> queues) throws IOException {
        byte[] body = "".getBytes();
        int messagesPerQueue = params.messageCount / queues.size();
        for (String queue : queues) {
            for (int i = 0; i < messagesPerQueue; i++) {
                channel.basicPublish("", queue, null, body);
            }
        }
        //ensure that all the messages have reached the queues
        for (String queue : queues) {
            channel.queueDeclarePassive(queue);
        }
    }

    protected long drain(QueueingConsumer c) throws IOException {
        long start = System.nanoTime();
        try {
            for (int i = 0; i < params.messageCount; i++) {
                long tag = c.nextDelivery().getEnvelope().getDeliveryTag();
                channel.basicAck(tag, false);
            }
        } catch (InterruptedException e) {
            IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
        long finish = System.nanoTime();
        return finish - start;
    }

    public long run() throws IOException, TimeoutException {
        connectionFactory.setHost(params.host);
        connectionFactory.setPort(params.port);
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        channel.basicQos(1);
        QueueingConsumer consumer = new QueueingConsumer(channel);
        try {
            publish(consume(consumer));
            return drain(consumer);
        } finally {
            connection.abort();
        }
    }

    public static void main(String[] args) throws Exception {
        CommandLine cmd = Parameters.parseCommandLine(args);
        if (cmd == null) return;
        Parameters params = new Parameters(cmd);
        System.out.print(params.toString());
        QosScaling test = new QosScaling(params);
        long result = test.run();
        System.out.println(" -> " + result / 1000000 + "ms");
    }

}
