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

class SimpleScenarioStats extends Stats implements ScenarioStats {
    private static final int ignoreFirst = 3;

    private long elapsed;
    private long ignored = ignoreFirst;

    public SimpleScenarioStats(long interval) {
        super(interval);
    }

    @Override
    protected void report(long now, long elapsed) {
        if (ignored > 0) {
            ignored--;
        }
        else if (ignored == 0) {
            cumulativeLatencyTotal = 0;
            latencyCountTotal      = 0;
            sendCountTotal         = 0;
            recvCountTotal         = 0;
            ignored = -1;
        }
        else {
            this.elapsed += elapsed;
        }
    }

    @Override
    public void print() {
        System.out.println("Sent: " + getSendRate() + " msg/s");
        System.out.println("Recv: " + getRecvRate() + " msg/s");
        if (latencyCountTotal > 0) {
            System.out.println("Avg latency: " + cumulativeLatencyTotal / (1000L * latencyCountTotal) + "us");
        }
    }

    public double getSendRate() {
        return 1000.0 * sendCountTotal / elapsed;
    }

    public double getRecvRate() {
        return 1000.0 * recvCountTotal / elapsed;
    }
}
