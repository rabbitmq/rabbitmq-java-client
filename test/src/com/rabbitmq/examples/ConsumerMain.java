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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.BlockingCell;

public class ConsumerMain implements Runnable {
    public static final int SUMMARY_EVERY_MS = 1000;

    public static final int ACK_BATCH_SIZE = 10;

    public static int optArg(String[] args, int index, int def) {
        return (args.length > index) ? Integer.parseInt(args[index]) : def;
    }

    public static String optArg(String[] args, int index, String def) {
        return (args.length > index) ? args[index] : def;
    }

    public static boolean optArg(String[] args, int index, boolean def) {
        return (args.length > index) ? Boolean.valueOf(args[index]).booleanValue() : def;
    }

    public static void main(String[] args) {
        try {
            String hostName = optArg(args, 0, "localhost");
            int portNumber = optArg(args, 1, AMQP.PROTOCOL.PORT);
            boolean writeStats = optArg(args, 2, true);
            final Connection conn = new ConnectionFactory().newConnection(hostName, portNumber);
            System.out.println("Channel 0 fully open.");
            new ConsumerMain(conn, writeStats).run();
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
            // no special error processing required
        }
    }

    public Connection _connection;

    public boolean _writeStats;

    public ConsumerMain(Connection connection, boolean writeStats) {
        _connection = connection;
        _writeStats = writeStats;
        System.out.println((_writeStats ? "WILL" : "WON'T") + " write statistics.");
    }

    public void run() {
        try {
            runIt();
        } catch (IOException ex) {
            System.err.println("hit IOException in ConsumerMain: trace follows");
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private void runIt() throws IOException {
        Channel channel = _connection.createChannel();

        String queueName = "test queue";
        channel.queueDeclare(queueName);

        String exchangeName = "test completion";
        channel.exchangeDeclare(exchangeName, "fanout", false, false, true, null);

        String completionQueue = channel.queueDeclare().getQueue();
        channel.queueBind(completionQueue, exchangeName, "");

        LatencyExperimentConsumer callback = new LatencyExperimentConsumer(channel, queueName);
        
        channel.basicConsume(queueName, true, callback);
        channel.basicConsume(completionQueue, true, "completion", callback);
        callback.report(_writeStats);
       
        System.out.println("Deleting test queue.");
        channel.queueDelete(queueName);

        System.out.println("Deleting completion queue.");
        channel.queueDelete(completionQueue);
        
        System.out.println("Closing the channel.");
        channel.close();
        
        System.out.println("Closing the connection.");
        _connection.close();
        
        System.out.println("Leaving ConsumerMain.run().");
    }

    public static class LatencyExperimentConsumer extends DefaultConsumer {

        public final String _queueName;

        public long _startTime;

        public long _mostRecentTime;

        public int _received;

        public int _previousReceived;

        public long _previousReportTime;

        public long[] _deltas;

        public final BlockingCell<Object> _blocker;

        public long _nextSummaryTime;

        public boolean _noAck = true;

        public LatencyExperimentConsumer(Channel ch, String queueName) {
            super(ch);
            _queueName = queueName;
            _received = 0;
            _previousReceived = 0;
            _deltas = null;
            _blocker = new BlockingCell<Object>();
        }

        public void report(boolean writeStats) throws IOException {
            Object sentinel = _blocker.uninterruptibleGet();
            if (sentinel instanceof ShutdownSignalException) {
                System.out.println("Aborted with shutdown signal in consumer.");
                System.exit(1);
            }

            long totalDelta = _mostRecentTime - _startTime;

            long maxL, minL;
            double sumL;

            maxL = Long.MIN_VALUE;
            minL = Long.MAX_VALUE;
            sumL = 0.0;

            int messageCount = _received;

            for (int i = 0; i < messageCount; i++) {
                long v = _deltas[i];
                if (v > maxL)
                    maxL = v;
                if (v < minL)
                    minL = v;
                sumL += v;
            }

            double avgL = sumL / messageCount;
            System.out.println("CONSUMER -  Message count: " + messageCount);
            System.out.println("Total time, milliseconds: " + totalDelta);
            System.out.println("Overall messages-per-second: " + (messageCount / (totalDelta / 1000.0)));
            System.out.println("Min latency, milliseconds: " + minL);
            System.out.println("Avg latency, milliseconds: " + avgL);
            System.out.println("Max latency, milliseconds: " + maxL);

            if (writeStats) {
                PrintStream o = new PrintStream(new FileOutputStream("simple-latency-experiment.dat"));
                for (int i = 0; i < messageCount; i++) {
                    o.println(i + " " + _deltas[i]);
                }
                o.close();

                int[] bins = new int[(int) maxL + 1];
                for (int i = 0; i < messageCount; i++) {
                    if (_deltas[i] != 0) {
                        bins[(int) _deltas[i]]++;
                    }
                }

                o = new PrintStream(new FileOutputStream("simple-latency-bins.dat"));
                for (int i = 0; i < bins.length; i++) {
                    o.println(i + " " + bins[i]);
                }
                o.close();
            }
        }

        @Override public void handleShutdownSignal(String consumerTag,
                                                   ShutdownSignalException sig)
        {
            System.out.println("Shutdown signal terminating consumer " + consumerTag +
                               " with signal " + sig);
            if (sig.getCause() != null) {
                sig.printStackTrace();
            }
            _blocker.setIfUnset(sig);
        }

        @Override public void handleDelivery(String consumerTag,
                                             Envelope envelope,
                                             AMQP.BasicProperties properties,
                                             byte[] body)
            throws IOException
        {
            if ("completion".equals(consumerTag)) {
                System.out.println("Got completion message.");
                finish();
                return;
            }

            if (body.length == 0) {
                return;
            }

            long now = System.currentTimeMillis();
            DataInputStream d = new DataInputStream(new ByteArrayInputStream(body));
            int messagesRemaining = d.readInt();
            long msgStartTime = d.readLong();

            _mostRecentTime = System.currentTimeMillis();

            if (_deltas == null) {
                _startTime = now;
                _previousReportTime = _startTime;
                _nextSummaryTime = _startTime + SUMMARY_EVERY_MS;
                _deltas = new long[messagesRemaining + 1];
            }

            if (msgStartTime != -1) {
                _deltas[_received++] = now - msgStartTime;

                if (!_noAck && ((_received % ACK_BATCH_SIZE) == 0)) {
                    getChannel().basicAck(0, true);
                }
            }

            if (now > _nextSummaryTime) {
                summariseProgress(now);
                _nextSummaryTime += SUMMARY_EVERY_MS;
            }

            if (messagesRemaining == 0) {
                finish();
            }
        }

        public void finish() throws IOException {
            if (!_noAck)
                getChannel().basicAck(0, true);
            _blocker.setIfUnset(new Object());
        }

        public void summariseProgress(long now) {
            int countOverInterval = _received - _previousReceived;
            double intervalRate = countOverInterval / ((now - _previousReportTime) / 1000.0);
            _previousReceived = _received;
            _previousReportTime = now;
            System.out.println((now - _startTime) + " ms: Received " + _received + " - " + countOverInterval + " since last report (" + (int) intervalRate
                    + " Hz)");
        }
    }
}
