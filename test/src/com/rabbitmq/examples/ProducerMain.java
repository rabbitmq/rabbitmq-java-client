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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

public class ProducerMain implements Runnable {
    public static final int SUMMARY_EVERY_MS = 1000;

    public static final int LATENCY_MESSAGE_COUNT = 60000;

    public static final int SEND_RATE = 100000;

    public static final int OUTSTANDING_LIMIT = Integer.MAX_VALUE;

    public static final int POLL_MS = 2;

    public static int optArg(String name, String[] args, int index, int def) {
        return summariseArg(name, (args.length > index) ? Integer.parseInt(args[index]) : def);
    }

    public static String optArg(String name, String[] args, int index, String def) {
        return summariseArg(name, (args.length > index) ? args[index] : def);
    }

    public static boolean optArg(String name, String[] args, int index, boolean def) {
        return summariseArg(name, (args.length > index) ? Boolean.valueOf(args[index]).booleanValue() : def);
    }

    public static int summariseArg(String name, int value) {
        System.out.println(name + " = " + value);
        return value;
    }

    public static String summariseArg(String name, String value) {
        System.out.println(name + " = " + value);
        return value;
    }

    public static boolean summariseArg(String name, boolean value) {
        System.out.println(name + " = " + value);
        return value;
    }

    public static void main(String[] args) {
        try {
            String hostName = optArg("hostName", args, 0, "localhost");
            int portNumber = optArg("portNumber", args, 1, AMQP.PROTOCOL.PORT);
            int rateLimit = optArg("rateLimit", args, 2, SEND_RATE);
            int messageCount = optArg("messageCount", args, 3, LATENCY_MESSAGE_COUNT);
            boolean sendCompletion = optArg("sendCompletion", args, 4, false);
            int commitEvery = optArg("commitEvery", args, 5, -1);
            boolean sendLatencyInfo = optArg("sendLatencyInfo", args, 6, true);
            final Connection conn = new ConnectionFactory().newConnection(hostName, portNumber);
            //if (commitEvery > 0) { conn.getSocket().setTcpNoDelay(true); }
            System.out.println("Channel 0 fully open.");
            new ProducerMain(conn, rateLimit, messageCount, sendCompletion, commitEvery, sendLatencyInfo).run();
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            // no special processing required
        }
    }

    public Connection _connection;

    public Channel _channel;

    public int _rateLimit;

    public int _messageCount;

    public boolean _sendCompletion;

    public int _commitEvery;

    public boolean _sendLatencyInfo;

    public ProducerMain(Connection connection, int rateLimit, int messageCount, boolean sendCompletion, int commitEvery, boolean sendLatencyInfo) {
        _connection = connection;
        _rateLimit = rateLimit;
        _messageCount = messageCount;
        _sendCompletion = sendCompletion;
        _commitEvery = commitEvery;
        _sendLatencyInfo = sendLatencyInfo;
    }

    public boolean shouldPersist() {
        return _commitEvery >= 0;
    }

    public boolean shouldCommit() {
        return _commitEvery > 0;
    }

    public void run() {
        try {
            runIt();
        } catch (IOException ex) {
            throw new RuntimeException(ex); // wrap and re-throw
        }
    }

    private void runIt() throws IOException {
        _channel = _connection.createChannel();

        String queueName = "test queue";
        _channel.queueDeclare(queueName, shouldPersist());
        
        if (shouldCommit()) {
            _channel.txSelect();
        }
        sendBatch(queueName);

        if (_sendCompletion) {
            // Declaring this exchange as auto-delete is a bit dodgy because of a
            // race condition with the consumer declaring the same exchange to be
            // auto-delete and hence pulling the rug out from underneath the producer's
            // feet.
            // Hence we're delaying a possible re-declaration until as late as possible.
            // Ideally you would use a global lock around both critical sections,
            // but thread safety has gone out of fashion these days.
            String exchangeName = "test completion";
            _channel.exchangeDeclare(exchangeName, "fanout", false, false, true, null);
            _channel.basicPublish(exchangeName, "", MessageProperties.BASIC, new byte[0]);
            if (shouldCommit())
                _channel.txCommit();
        }

        _channel.close();
        System.out.println("Closing.");
        _connection.close();
        System.out.println("Leaving ProducerMain.run().");
    }

    public void primeServer(String queueName) throws IOException {
        System.out.println("Priming server...");
        for (int i = 0; i < 2000; i++) {
            _channel.basicPublish("", queueName, MessageProperties.MINIMAL_BASIC, new byte[0]);
        }
        sleep(500);
        System.out.println("...starting.");
    }

    public void sendBatch(String queueName) throws IOException {
        //primeServer(queueName);

        long startTime = System.currentTimeMillis();
        int sent = 0;
        int previousSent = 0;
        long previousReportTime = startTime;

        long nextSummaryTime = startTime + SUMMARY_EVERY_MS;
        byte[] message = new byte[256];
        for (int i = 0; i < _messageCount; i++) {
            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(acc);
            long now = System.currentTimeMillis();
            d.writeInt(_messageCount - i - 1);
            if (_sendLatencyInfo) {
                d.writeLong(now);
            } else {
                d.writeLong(-1);
            }
            d.flush();
            acc.flush();
            byte[] message0 = acc.toByteArray();
            System.arraycopy(message0, 0, message, 0, message0.length);
            _channel.basicPublish("", queueName, shouldPersist() ? MessageProperties.MINIMAL_PERSISTENT_BASIC : MessageProperties.MINIMAL_BASIC,
                    message);
            sent++;
            if (shouldCommit()) {
                if ((sent % _commitEvery) == 0) {
                    _channel.txCommit();
                }
            }
            if (now > nextSummaryTime) {
                summariseProgress(startTime, now, sent, previousReportTime, previousSent);
                previousSent = sent;
                previousReportTime = now;
                nextSummaryTime += SUMMARY_EVERY_MS;
            }
            while (((1000.0 * i) / (now - startTime)) > _rateLimit) {
                sleep(POLL_MS);
                now = System.currentTimeMillis();
            }
        }

        long stopTime = System.currentTimeMillis();
        long totalDelta = stopTime - startTime;
        report(totalDelta);
    }

    public void report(long totalDelta) {
        System.out.println("PRODUCER - Message count: " + _messageCount);
        System.out.println("Total time, milliseconds: " + totalDelta);
        System.out.println("Overall messages-per-second: " + (_messageCount / (totalDelta / 1000.0)));
    }

    public void summariseProgress(long startTime, long now, int sent, long previousReportTime, int previousSent) {
        int countOverInterval = sent - previousSent;
        double intervalRate = countOverInterval / ((now - previousReportTime) / 1000.0);
        System.out.println((now - startTime) + " ms: Sent " + sent + " - " + countOverInterval + " since last report (" + (int) intervalRate + " Hz)");
    }
}
