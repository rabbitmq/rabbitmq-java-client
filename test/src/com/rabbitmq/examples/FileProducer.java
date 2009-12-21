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

package com.rabbitmq.examples;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.AMQP.BasicProperties;

public class FileProducer {
    public static void main(String[] args) {
	Options options = new Options();
	options.addOption(new Option("h", "host", true, "broker host"));
	options.addOption(new Option("p", "port", true, "broker port"));
	options.addOption(new Option("t", "type", true, "exchange type"));
	options.addOption(new Option("e", "exchange", true, "exchange name"));
	options.addOption(new Option("k", "routing-key", true, "routing key"));

        CommandLineParser parser = new GnuParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            String hostName = strArg(cmd, 'h', "localhost");
            int portNumber = intArg(cmd, 'p', AMQP.PROTOCOL.PORT);
	    String exchangeType = strArg(cmd, 't', "direct");
	    String exchange = strArg(cmd, 'e', null);
	    String routingKey = strArg(cmd, 'k', null);

            ConnectionFactory connFactory = new ConnectionFactory();
            Connection conn = connFactory.newConnection(hostName, portNumber);

            final Channel ch = conn.createChannel();

	    if (exchange == null) {
		System.err.println("Please supply exchange name to send to (-e)");
		System.exit(2);
	    }
	    if (routingKey == null) {
		System.err.println("Please supply routing key to send to (-k)");
		System.exit(2);
	    }
	    ch.exchangeDeclare(exchange, exchangeType);

	    for (String filename : cmd.getArgs()) {
		System.out.print("Sending " + filename + "...");
		File f = new File(filename);
		FileInputStream i = new FileInputStream(f);
		byte[] body = new byte[(int) f.length()];
		i.read(body);
		i.close();

		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put("filename", filename);
		headers.put("length", (int) f.length());
		BasicProperties props = new BasicProperties(null, null, headers, null,
							    null, null, null, null,
							    null, null, null, null,
							    null, null);
		ch.basicPublish(exchange, routingKey, props, body);
		System.out.println(" done.");
	    }

	    conn.close();
        } catch (Exception ex) {
            System.err.println("Main thread caught exception: " + ex);
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static String strArg(CommandLine cmd, char opt, String def) {
        return cmd.getOptionValue(opt, def);
    }

    private static int intArg(CommandLine cmd, char opt, int def) {
        return Integer.parseInt(cmd.getOptionValue(opt, Integer.toString(def)));
    }
}
