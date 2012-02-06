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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VaryingScenarioStats implements ScenarioStats {
    private Map<Object, SimpleScenarioStats> results = new HashMap<Object, SimpleScenarioStats>();
    private List<Object> keys = new ArrayList<Object>();

    public VaryingScenarioStats() {
    }

    public SimpleScenarioStats next(List<?> value) {
        SimpleScenarioStats stats = new SimpleScenarioStats(1000L);
        keys.add(value);
        results.put(value, stats);
        return stats;
    }

    @Override
    public void print() {
        for (Object key : keys) {
            System.out.println("Results for " + key);
            results.get(key).print();
        }
    }
}
