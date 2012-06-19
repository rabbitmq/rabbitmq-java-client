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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.AMQP.BasicProperties;

/**
 * Java Application which uses an AMQP server to transmit entire files as single
 * messages. No refinements such as restarting interrupted transfers or
 * file segmentation are implemented. Each file is transmitted to a given
 * exchange/routing-key combination using Basic.Publish, with
 * <ul>
 * <li>metadata about the file in the Basic headers table:
 * <ul>
 * <li>"<code>filename</code>" is the name of the file on the producer's side</li>
 * <li>"<code>length</code>" is the length of the file, in bytes</li>
 * </ul>
 * </li>
 * <li>the content of the file in the Basic message body.</li>
 * </ul>
 * <p>After the files are sent <code>FileProducer</code> will terminate.</p>
 * @see FileConsumer
 */
public class FileProducer {

    /**
     * @param args command-line arguments:
     * <p>
     * Optional arguments are:
     * </p>
     * <ul>
     * <li><code>-h</code> <i>AMQP-uri</i> - the AMQP uri to connect to the broker to use. Default
     * <code>amqp://localhost</code>.</li>
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * <li><code>-p</code> <i>broker-port</i> - the port number to contact the broker on. Default
     * <code>5672</code>.</li>
     * <li><code>-e</code> <i>exchange-name</i> - the name of the exchange to use. The program declares and
     * publishes to this exchange name.</li>
     * <li><code>-t</code> <i>exchange-type</i> - the type of exchange to declare. One of <code>direct</code>,
     * <code>fanout</code> or <code>topic</code>. Default <code>direct</code>.</li>
     * <li><code>-k</code> <i>routing-key</i> - the routing-key to use when sending each file. Used in
     * conjunction with the exchange name for each file delivery.</li>
     * </ul>
     * <p>
     * Zero or more filenames can be specified after the options. Each file will be sent to the same configured
     * exchange/routing-key in the order of appearance on the command-line.
     * </p>
     */
    public static void main(String[] args) {
	Options options = new Options();
	options.addOption(new Option("h", "uri", true, "AMQP URI"));
	options.addOption(new Option("p", "port", true, "broker port"));
	options.addOption(new Option("t", "type", true, "exchange type"));
	options.addOption(new Option("e", "exchange", true, "exchange name"));
	options.addOption(new Option("k", "routing-key", true, "routing key"));

        CommandLineParser parser = new GnuParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            String uri = strArg(cmd, 'h', "amqp://localhost");
	    String exchangeType = strArg(cmd, 't', "direct");
	    String exchange = strArg(cmd, 'e', null);
	    String routingKey = strArg(cmd, 'k', null);

            ConnectionFactory connFactory = new ConnectionFactory();
            connFactory.setUri(uri);
            Connection conn = connFactory.newConnection();

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
		BasicProperties props = new BasicProperties.Builder().headers(headers).build();
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
}
