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

public abstract class Stats {
    protected long    interval;
    protected boolean sendStatsEnabled;
    protected boolean recvStatsEnabled;
    protected boolean returnStatsEnabled;
    protected boolean confirmStatsEnabled;

    protected long    startTime;
    protected long    lastStatsTime;

    protected int     sendCount;
    protected int     returnCount;
    protected int     confirmCount;
    protected int     nackCount;
    protected int     recvCount;

    protected int     latencyCount;
    protected long    minLatency;
    protected long    maxLatency;
    protected long    cumulativeLatency;

    public Stats(long interval,
                 boolean sendStatsEnabled, boolean recvStatsEnabled,
                 boolean returnStatsEnabled, boolean confirmStatsEnabled) {
        this.interval            = interval;
        this.sendStatsEnabled    = sendStatsEnabled;
        this.recvStatsEnabled    = recvStatsEnabled;
        this.returnStatsEnabled  = returnStatsEnabled;
        this.confirmStatsEnabled = confirmStatsEnabled;
        startTime = System.currentTimeMillis();
        reset(startTime);
    }

    private void reset(long t) {
        lastStatsTime     = t;

        sendCount         = 0;
        returnCount       = 0;
        confirmCount      = 0;
        nackCount         = 0;
        recvCount         = 0;

        latencyCount      = 0;
        minLatency        = Long.MAX_VALUE;
        maxLatency        = Long.MIN_VALUE;
        cumulativeLatency = 0L;
    }

    private void report() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastStatsTime;

        if (elapsed >= interval) {
            report(now, elapsed);
            reset(now);
        }
    }

    protected abstract void report(long now, long elapsed);

    public synchronized void handleSend() {
        sendCount++;
        report();
    }

    public synchronized void handleReturn() {
        returnCount++;
        report();
    }

    public synchronized void handleConfirm(int numConfirms) {
        confirmCount+=numConfirms;
        report();
    }

    public synchronized void handleNack(int numAcks) {
        nackCount+=numAcks;
        report();
    }

    public synchronized void handleRecv(long latency) {
        recvCount++;
        if (latency > 0) {
            minLatency = Math.min(minLatency, latency);
            maxLatency = Math.max(maxLatency, latency);
            cumulativeLatency += latency;
            latencyCount++;
        }
        report();
    }

}
