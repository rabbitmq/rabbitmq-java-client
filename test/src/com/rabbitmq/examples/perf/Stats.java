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

package com.rabbitmq.examples.perf;

/**
 * Timing statistics collection framework
 */
public abstract class Stats {
    private final long interval;              // collection interval in ms

    protected long startTime;                 // timestamp in ms
    protected long lastStatsTime;             // timestamp in ms

    protected int  sendCountInterval;         // occurrences in interval
    protected int  returnCountInterval;       // occurrences in interval
    protected int  confirmCountInterval;      // occurrences in interval
    protected int  nackCountInterval;         // occurrences in interval
    protected int  recvCountInterval;         // occurrences in interval

    protected int  sendCountTotal;            // total occurrences
    protected int  recvCountTotal;            // total occurrences

    protected int  latencyCountInterval;      // occurrences in interval
    protected int  latencyCountTotal;         // total occurrences
    protected long minLatency;                // minimum latency observed in nanos
    protected long maxLatency;                // maximum latency observed in nanos
    protected long cumulativeLatencyInterval; // latency so far in interval in nanos
    protected long cumulativeLatencyTotal;    // total latency for all messages in nanos

    protected long elapsedInterval;           // ms elapsed in this interval at last event
    protected long elapsedTotal;              // ms elapsed total (completed intervals)

    /**
     * @param interval milliseconds between reports
     */
    public Stats(long interval) {
        this.interval = interval;
        startTime = System.currentTimeMillis();
        reset(startTime);
    }

    private void reset(long t) {
        lastStatsTime             = t;

        sendCountInterval         = 0;
        returnCountInterval       = 0;
        confirmCountInterval      = 0;
        nackCountInterval         = 0;
        recvCountInterval         = 0;

        minLatency                = Long.MAX_VALUE;
        maxLatency                = Long.MIN_VALUE;
        latencyCountInterval      = 0;
        cumulativeLatencyInterval = 0L;
    }

    /**
     * Report (and reset) stats if <code>interval</code> elapsed since <code>lastStatsTime</code>
     */
    private void maybeReport() {
        long now = System.currentTimeMillis();
        elapsedInterval = now - lastStatsTime;

        if (elapsedInterval >= interval) {
            elapsedTotal += elapsedInterval;
            report(now);
            reset(now);
        }
    }

    protected abstract void report(long now);

    /**
     * Accumulate stats for a Send
     */
    public synchronized void handleSend() {
        sendCountInterval++;
        sendCountTotal++;
        maybeReport();
    }

    /**
     * Accumulate stats for a Return
     */
    public synchronized void handleReturn() {
        returnCountInterval++;
        maybeReport();
    }

    /**
     * Accumulate stats for a Confirm
     * @param numConfirms number of confirms
     */
    public synchronized void handleConfirm(int numConfirms) {
        confirmCountInterval +=numConfirms;
        maybeReport();
    }

    /**
     * Accumulate stats for a Nack
     * @param numNacks number of Nacks
     */
    public synchronized void handleNack(int numNacks) {
        nackCountInterval +=numNacks;
        maybeReport();
    }

    /**
     * Accumulate stats for a Recv
     * @param latency this message latency in nanos
     */
    public synchronized void handleRecv(long latency) {
        recvCountInterval++;
        recvCountTotal++;
        if (latency > 0) {
            minLatency = Math.min(minLatency, latency);
            maxLatency = Math.max(maxLatency, latency);
            cumulativeLatencyInterval += latency;
            cumulativeLatencyTotal += latency;
            latencyCountInterval++;
            latencyCountTotal++;
        }
        maybeReport();
    }

}
