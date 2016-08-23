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

package com.rabbitmq.examples.perf;

public abstract class Stats {
    protected final long    interval;

    protected final long    startTime;
    protected long    lastStatsTime;

    protected int     sendCountInterval;
    protected int     returnCountInterval;
    protected int     confirmCountInterval;
    protected int     nackCountInterval;
    protected int     recvCountInterval;

    protected int     sendCountTotal;
    protected int     recvCountTotal;

    protected int     latencyCountInterval;
    protected int     latencyCountTotal;
    protected long    minLatency;
    protected long    maxLatency;
    protected long    cumulativeLatencyInterval;
    protected long    cumulativeLatencyTotal;

    protected long    elapsedInterval;
    protected long    elapsedTotal;

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

    private void report() {
        long now = System.currentTimeMillis();
        elapsedInterval = now - lastStatsTime;

        if (elapsedInterval >= interval) {
            elapsedTotal += elapsedInterval;
            report(now);
            reset(now);
        }
    }

    protected abstract void report(long now);

    public synchronized void handleSend() {
        sendCountInterval++;
        sendCountTotal++;
        report();
    }

    public synchronized void handleReturn() {
        returnCountInterval++;
        report();
    }

    public synchronized void handleConfirm(int numConfirms) {
        confirmCountInterval +=numConfirms;
        report();
    }

    public synchronized void handleNack(int numAcks) {
        nackCountInterval +=numAcks;
        report();
    }

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
        report();
    }

}
