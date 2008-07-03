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
//   The Initial Developers of the Original Code are LShift Ltd.,
//   Cohesive Financial Technologies LLC., and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd., Cohesive Financial Technologies
//   LLC., and Rabbit Technologies Ltd. are Copyright (C) 2007-2008
//   LShift Ltd., Cohesive Financial Technologies LLC., and Rabbit
//   Technologies Ltd.;
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;

public class ManyConnections {
    public static double rate;
    public static int connectionCount;
    public static int channelPerConnectionCount;

    public static int totalCount() {
	return connectionCount * channelPerConnectionCount;
    }

    public static void main(String[] args) {
        try {
	    if (args.length < 3) {
		System.err.println("Usage: ManyConnections hostName connCount chanPerConnCount [rate [port]]");
		System.exit(2);
	    }

            String hostName = args[0];
	    connectionCount = Integer.parseInt(args[1]);
	    channelPerConnectionCount = Integer.parseInt(args[2]);
	    rate = (args.length > 3) ? Double.parseDouble(args[3]) : 1.0;
            int portNumber = (args.length > 4) ? Integer.parseInt(args[4]) : AMQP.PROTOCOL.PORT;

	    for (int i = 0; i < connectionCount; i++) {
		final Connection conn = new ConnectionFactory().newConnection(hostName, portNumber);

		for (int j = 0; j < channelPerConnectionCount; j++) {
		    final Channel ch = conn.createChannel();
		    final int ticket = ch.accessRequest("/data");

		    final int threadNumber = i * channelPerConnectionCount + j;
		    System.out.println("Starting "+threadNumber+" "+ch+" thread...");
		    new Thread(new Runnable() {
			    public void run() {
				runChannel(threadNumber, conn, ch, ticket);
			    }
			}).start();
		}
	    }
	    System.out.println("Started " + totalCount() + " channels and threads.");
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void runChannel(int threadNumber,
				  Connection conn,
				  Channel ch,
				  int ticket)
    {
	try {
	    int delayLen = (int) (1000 / rate);
	    long startTime = System.currentTimeMillis();

	    int msgCount = 0;
	    String queueName = "ManyConnections";
	    ch.queueDeclare(ticket, queueName);

	    QueueingConsumer consumer = new QueueingConsumer(ch);
	    ch.basicConsume(ticket, queueName, true, consumer);
	    while (true) {
		String toSend = threadNumber + "/" + msgCount++;
		ch.basicPublish(ticket, "", queueName, null, toSend.getBytes());
		Thread.sleep(delayLen);

		QueueingConsumer.Delivery delivery = consumer.nextDelivery();
		if (threadNumber == 0) {
		    long now = System.currentTimeMillis();
		    double delta = (now - startTime) / 1000.0;
		    double actualRate = msgCount / delta;
		    double totalRate = totalCount() * actualRate;
		    System.out.println(threadNumber + " got message: " +
				       new String(delivery.getBody()) + "; " +
				       msgCount + " messages in " + delta + " seconds (" +
				       actualRate + " Hz * " +
				       totalCount() + " channels -> " + totalRate + " Hz)");
		}
	    }
	} catch (Exception e) {
            System.err.println("Thread "+threadNumber+" caught exception: " + e);
            e.printStackTrace();
	}
    }
}
