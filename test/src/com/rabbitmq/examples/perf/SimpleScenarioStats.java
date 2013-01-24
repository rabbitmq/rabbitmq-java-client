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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.examples.perf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SimpleScenarioStats extends Stats implements ScenarioStats {
    private static final int IGNORE_FIRST = 3;

    private List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
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
