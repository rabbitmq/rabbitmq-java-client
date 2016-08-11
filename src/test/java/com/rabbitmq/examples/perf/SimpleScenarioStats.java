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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SimpleScenarioStats extends Stats implements ScenarioStats {
    private static final int IGNORE_FIRST = 3;

    private final List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
    private long elapsedTotalToIgnore;
    private long minMsgSize;

    public SimpleScenarioStats(long interval) {
        super(interval);
    }

    protected void report(long now) {
        if (samples.size() == IGNORE_FIRST) {
            cumulativeLatencyTotal = 0;
            latencyCountTotal      = 0;
            sendCountTotal         = 0;
            recvCountTotal         = 0;
            elapsedTotalToIgnore   = elapsedTotal;
        }

        Map<String, Object> sample = new HashMap<String, Object>();
        sample.put("send-msg-rate", rate(sendCountInterval, elapsedInterval));
        sample.put("send-bytes-rate", rate(sendCountInterval, elapsedInterval) * minMsgSize);
        sample.put("recv-msg-rate", rate(recvCountInterval, elapsedInterval));
        sample.put("recv-bytes-rate", rate(recvCountInterval, elapsedInterval) * minMsgSize);
        sample.put("elapsed",   elapsedTotal);
        if (latencyCountInterval > 0) {
            sample.put("avg-latency", intervalAverageLatency());
            sample.put("min-latency", minLatency / 1000L);
            sample.put("max-latency", maxLatency / 1000L);
        }
        samples.add(sample);
    }

    public Map<String, Object> results() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("send-msg-rate", getSendRate());
        map.put("send-bytes-rate", getSendRate() * minMsgSize);
        map.put("recv-msg-rate", getRecvRate());
        map.put("recv-bytes-rate", getRecvRate() * minMsgSize);
        if (latencyCountTotal > 0) {
            map.put("avg-latency", overallAverageLatency());
        }
        map.put("samples", samples);
        return map;
    }

    public void setup(MulticastParams params) {
        this.minMsgSize = params.getMinMsgSize();
    }

    public double getSendRate() {
        return rate(sendCountTotal, elapsedTotal - elapsedTotalToIgnore);
    }

    public double getRecvRate() {
        return rate(recvCountTotal, elapsedTotal - elapsedTotalToIgnore);
    }

    private double rate(long count, long elapsed) {
        return elapsed == 0 ? 0.0 : (1000.0 * count / elapsed);
    }

    private long overallAverageLatency() {
        return cumulativeLatencyTotal / (1000L * latencyCountTotal);
    }

    private long intervalAverageLatency() {
        return cumulativeLatencyInterval / (1000L * latencyCountInterval);
    }
}
