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
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.examples;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.AMQP.BasicProperties;

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
            final String uri = optArg("uri", args, 0, "amqp://localhost");
            int rateLimit = optArg("rateLimit", args, 1, SEND_RATE);
            int messageCount = optArg("messageCount", args, 2, LATENCY_MESSAGE_COUNT);
            boolean sendCompletion = optArg("sendCompletion", args, 3, false);
            int commitEvery = optArg("commitEvery", args, 4, -1);
            boolean sendLatencyInfo = optArg("sendLatencyInfo", args, 5, true);
            final Connection conn = new ConnectionFactory(){{setUri(uri);}}.newConnection();
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

    public final Connection _connection;

    public Channel _channel;

    public final int _rateLimit;

    public final int _messageCount;

    public final boolean _sendCompletion;

    public final int _commitEvery;

    public final boolean _sendLatencyInfo;

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
         catch (TimeoutException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void runIt() throws IOException, TimeoutException {
        _channel = _connection.createChannel();

        String queueName = "test queue";
        _channel.queueDeclare(queueName, true, false, false, null);

        if (shouldCommit()) {
            _channel.txSelect();
        }
        sendBatch(queueName);

        if (_sendCompletion) {
            String exchangeName = "test completion";
            _channel.exchangeDeclarePassive(exchangeName);
            _channel.basicPublish(exchangeName, "", MessageProperties.BASIC, new byte[0]);
            if (shouldCommit())
                _channel.txCommit();
            _channel.exchangeDelete(exchangeName);
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
        BasicProperties props = shouldPersist() ?
                MessageProperties.MINIMAL_PERSISTENT_BASIC :
                    MessageProperties.MINIMAL_BASIC;
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
            _channel.basicPublish("", queueName, props, message);
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
        System.out
                .println("PRODUCER -       Overall: "
                        + String.format("%d messages in %dms, a rate of %.2f msgs/sec", _messageCount,
                                totalDelta,
                                (_messageCount / (totalDelta / 1000.0))));
    }

    public void summariseProgress(long startTime, long now, int sent, long previousReportTime, int previousSent) {
        int countOverInterval = sent - previousSent;
        double intervalRate = countOverInterval / ((now - previousReportTime) / 1000.0);
        System.out.println((now - startTime) + " ms: Sent " + sent + " - "
                + countOverInterval + " since last report ("
                + (int) intervalRate + " Hz)");
    }
}
