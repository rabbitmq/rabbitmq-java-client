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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class QosScaling {

    protected static class Parameters {
        String host;
        int port;
        int messageCount;
        int queueCount;
        int emptyCount;

        public static CommandLine parseCommandLine(String[] args) {
            CLIHelper helper = CLIHelper.defaultHelper();
            helper.addOption(new Option("n", "messages", true, "number of messages to send"));
            helper.addOption(new Option("q", "queues",   true, "number of queues to route messages to"));
            helper.addOption(new Option("e", "empty",    true, "number of queues to leave empty"));
            
            return helper.parseCommandLine(args);
        }
        
        public Parameters(CommandLine cmd) {
            host         = cmd.getOptionValue("h", "localhost");
            port         = CLIHelper.getOptionValue(cmd, "p", 5672);
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
        Channel pubCh = connection.createChannel();
        pubCh.txSelect();
        byte[] body = "".getBytes();
        int messagesPerQueue = params.messageCount / queues.size();
        for (String queue : queues) {
            for (int i = 0; i < messagesPerQueue; i++) {
                pubCh.basicPublish("", queue, null, body);
            }
        }
        pubCh.txCommit();
        pubCh.close();
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

    public long run() throws IOException {
        connection = connectionFactory.newConnection(params.host, params.port);
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
