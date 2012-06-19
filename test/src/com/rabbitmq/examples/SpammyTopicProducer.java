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

import java.util.Random;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Java Application to send messages with randomly changing topic keys, as
 * fast as possible. This can be used to test the topic key cache in the broker.
 * <p/>
 * Periodically (every second) a summary of the current message rate and the
 * overall message rate is printed.
 */
public class SpammyTopicProducer {
    private static final String DEFAULT_EXCHANGE = "amq.topic"; // do not declare this
    private static final String DEFAULT_TOPIC_PREFIX = "top.";
    private static final int SUMMARISE_EVERY = 1000;

    /**
     * @param args command-line parameters:
     * <p>
     * One to four positional parameters:
     * </p>
     * <ul>
     * <li><i>AMQP-uri</i> -
     * the AMQP uri to connect to the broker to use. No default.
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * </li>
     * <li><i>topic-prefix</i> - the prefix of the topic published to. Default "<code>top.</code>".
     * </li>
     * <li><i>exchange</i> - name of the topic exchange to publish to. Default "<code>amq.topic</code>".
     * </li>
     * <li><i>message</i> - message to publish. Default is a (fixed) time-of-day message.
     * </li>
     * </ul>
     */
    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 4) {
                System.err.print("Usage: SpammyTopicProducer brokeruri [topic prefix\n" +
                                 "                                      [exchange\n" +
                                 "                                       [message]]]\n" +
                                 "where\n" +
                                 " - topic defaults to \"" + DEFAULT_TOPIC_PREFIX + "\",\n" +
                                 " - exchange defaults to \"" + DEFAULT_EXCHANGE + "\", and\n" +
                                 " - message defaults to a time-of-day message\n");
                System.exit(1);
            }
            String uri = args[0];
            String topicPrefix = (args.length > 1) ? args[1] : DEFAULT_TOPIC_PREFIX;
            String exchange = (args.length > 2) ? args[2] : DEFAULT_EXCHANGE;
            String message = (args.length > 3) ? args[3] :
                "the time is " + new java.util.Date().toString();

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();

            Channel ch = conn.createChannel();

            if (!DEFAULT_EXCHANGE.equals(exchange)) {
                ch.exchangeDeclare(exchange, "topic");
            }

            System.out.println("Sending to exchange " + exchange + ", prefix: " + topicPrefix);

            int thisTimeCount = 0;
            int allTimeCount = 0;
            long startTime = System.currentTimeMillis();
            long nextSummaryTime = startTime;
            while (true) {
                ch.basicPublish(exchange, topicPrefix + newSuffix(), null, message.getBytes());
                thisTimeCount++;
                allTimeCount++;
                long now = System.currentTimeMillis();
                if (now > nextSummaryTime) {
                    int thisTimeRate = (int)(1.0 * thisTimeCount / (now - nextSummaryTime + SUMMARISE_EVERY) * SUMMARISE_EVERY);
                    int allTimeRate = (int)(1.0 * allTimeCount / (now - startTime) * SUMMARISE_EVERY);
                    System.out.println(thisTimeRate + " / sec currently, " + allTimeRate + " / sec average");
                    thisTimeCount = 0;
                    nextSummaryTime = now + SUMMARISE_EVERY;
                }
            }

            //ch.close();
            //conn.close();
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String newSuffix() {
        return "" + new Random().nextLong();
    }
}
