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

package com.rabbitmq.examples;

import java.util.Random;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * This producer creates messages with constantly changing topic keys, sending as
 * fast as it can. This can be used to test the topic key cache in the broker.
 */

public class SpammyTopicProducer {
    public static final String DEFAULT_TOPIC_PREFIX = "top.";
    private static final int SUMMARISE_EVERY = 1000;

    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 4) {
                System.err.print("Usage: SpammyTopicProducer brokeruri [topic prefix\n" +
                                 "                                      [exchange\n" +
                                 "                                       [message]]]\n" +
                                 "where\n" +
                                 " - topic defaults to \""+DEFAULT_TOPIC_PREFIX+"\",\n" +
                                 " - exchange defaults to \"amq.topic\", and\n" +
                                 " - message defaults to a time-of-day message\n");
                System.exit(1);
            }
            String uri = (args.length > 0) ? args[0] : "amqp://localhost";
            String topicPrefix = (args.length > 1) ? args[1] : DEFAULT_TOPIC_PREFIX;
            String exchange = (args.length > 2) ? args[2] : null;
            String message = (args.length > 3) ? args[3] :
                "the time is " + new java.util.Date().toString();

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();

            Channel ch = conn.createChannel();

            if (exchange == null) {
                exchange = "amq.topic";
            } else {
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
