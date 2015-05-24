//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class StressManagement {
    protected static class Parameters {
        final String host;
        final int port;
        final int queueCount;
        final int channelCount;

        public static CommandLine parseCommandLine(String[] args) {
            CLIHelper helper = CLIHelper.defaultHelper();
            helper.addOption(new Option("q", "queues", true, "number of queues"));
            helper.addOption(new Option("c", "channels", true, "number of channels"));
            return helper.parseCommandLine(args);
        }

        public Parameters(CommandLine cmd) {
            host         = cmd.getOptionValue("h", "localhost");
            port         = CLIHelper.getOptionValue(cmd, "p", AMQP.PROTOCOL.PORT);
            queueCount   = CLIHelper.getOptionValue(cmd, "q", 5000);
            channelCount = CLIHelper.getOptionValue(cmd, "c", 100);
        }

        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("host="      + host);
            b.append(",port="     + port);
            b.append(",queues="   + queueCount);
            b.append(",channels=" + channelCount);
            return b.toString();
        }

    }

    protected final Parameters params;
    protected final ConnectionFactory connectionFactory =
        new ConnectionFactory();
    protected Connection connection;
    protected Channel publishChannel;
    protected Channel[] channels;

    public StressManagement(Parameters p) {
        params = p;
    }

    public long run() throws IOException, TimeoutException {
        connectionFactory.setHost(params.host);
        connectionFactory.setPort(params.port);
        connection = connectionFactory.newConnection();
        publishChannel = connection.createChannel();

        System.out.println("Declaring...");

        channels = new Channel[params.channelCount];
        for (int i = 0; i < params.channelCount; i++) {
            channels[i] = connection.createChannel();
        }

        for (int i = 0; i < params.queueCount; i++) {
            publishChannel.queueDeclare("queue-" + i, false, true, false, null);
            publishChannel.queueBind("queue-" + i, "amq.fanout", "");
        }

        System.out.println("Declaration complete, running...");

        while (true) {
            for (int i = 0; i < params.channelCount; i++) {
                publishChannel.basicPublish("amq.fanout", "", MessageProperties.BASIC, "".getBytes());
                for (int j = 0; j < params.queueCount; j++) {
                    while (channels[i].basicGet("queue-" + j, true) == null) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        CommandLine cmd = Parameters.parseCommandLine(args);
        if (cmd == null) return;
        Parameters params = new Parameters(cmd);
        System.out.println(params.toString());
        StressManagement test = new StressManagement(params);
        test.run();
    }
}
