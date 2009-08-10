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

import java.util.Random;

import com.rabbitmq.client.AMQP;
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
            if (args.length < 1 || args.length > 5) {
                System.err.print("Usage: SpammyTopicProducer brokerhostname [brokerport\n" +
                                 "                                           [topic prefix\n" +
                                 "                                            [exchange\n" +
                                 "                                             [message]]]]\n" +
                                 "where\n" +
                                 " - topic defaults to \""+DEFAULT_TOPIC_PREFIX+"\",\n" +
                                 " - exchange defaults to \"amq.topic\", and\n" +
                                 " - message defaults to a time-of-day message\n");
                System.exit(1);
            }
            String hostName = (args.length > 0) ? args[0] : "localhost";
            int portNumber = (args.length > 1) ? Integer.parseInt(args[1]) : AMQP.PROTOCOL.PORT;
            String topicPrefix = (args.length > 2) ? args[2] : DEFAULT_TOPIC_PREFIX;
            String exchange = (args.length > 3) ? args[3] : null;
            String message = (args.length > 4) ? args[4] :
                "the time is " + new java.util.Date().toString();

            Connection conn = new ConnectionFactory().newConnection(hostName, portNumber);

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
