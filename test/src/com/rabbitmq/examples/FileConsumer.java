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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class FileConsumer {
    public static void main(String[] args) {
	Options options = new Options();
	options.addOption(new Option("h", "uri", true, "AMQP URI"));
	options.addOption(new Option("q", "queue", true, "queue name"));
	options.addOption(new Option("t", "type", true, "exchange type"));
	options.addOption(new Option("e", "exchange", true, "exchange name"));
	options.addOption(new Option("k", "routing-key", true, "routing key"));
	options.addOption(new Option("d", "directory", true, "output directory"));

        CommandLineParser parser = new GnuParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            String uri = strArg(cmd, 'h', "amqp://localhost");
	    String requestedQueueName = strArg(cmd, 'q', "");
	    String exchangeType = strArg(cmd, 't', "direct");
	    String exchange = strArg(cmd, 'e', null);
	    String routingKey = strArg(cmd, 'k', null);
	    String outputDirName = strArg(cmd, 'd', ".");

	    File outputDir = new File(outputDirName);
	    if (!outputDir.exists() || !outputDir.isDirectory()) {
		System.err.println("Output directory must exist, and must be a directory.");
		System.exit(2);
	    }

            ConnectionFactory connFactory = new ConnectionFactory();
            connFactory.setUri(uri);
            Connection conn = connFactory.newConnection();

            final Channel ch = conn.createChannel();

            String queueName =
		(requestedQueueName.equals("")
		 ? ch.queueDeclare()
		 : ch.queueDeclare(requestedQueueName,
                                   false, false, false, null)).getQueue();

	    if (exchange != null || routingKey != null) {
		if (exchange == null) {
		    System.err.println("Please supply exchange name to bind to (-e)");
		    System.exit(2);
		}
		if (routingKey == null) {
		    System.err.println("Please supply routing key pattern to bind to (-k)");
		    System.exit(2);
		}
		ch.exchangeDeclare(exchange, exchangeType);
		ch.queueBind(queueName, exchange, routingKey);
	    }

            QueueingConsumer consumer = new QueueingConsumer(ch);
            ch.basicConsume(queueName, consumer);
            while (true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
		Map<String, Object> headers = delivery.getProperties().getHeaders();
		byte[] body = delivery.getBody();
		Object headerFilenameO = headers.get("filename");
		String headerFilename =
		    (headerFilenameO == null)
		    ? UUID.randomUUID().toString()
		    : headerFilenameO.toString();
		File givenName = new File(headerFilename);
		if (givenName.getName().equals("")) {
		    System.out.println("Skipping file with empty name: " + givenName);
		} else {
		    File f = new File(outputDir, givenName.getName());
		    System.out.print("Writing " + f + " ...");
		    FileOutputStream o = new FileOutputStream(f);
		    o.write(body);
		    o.close();
		    System.out.println(" done.");
		}
		ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        } catch (Exception ex) {
            System.err.println("Main thread caught exception: " + ex);
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static String strArg(CommandLine cmd, char opt, String def) {
        return cmd.getOptionValue(opt, def);
    }
}
