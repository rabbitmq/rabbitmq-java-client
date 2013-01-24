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

public class VaryingScenarioStats implements ScenarioStats {
    private Map<List<VariableValue>, SimpleScenarioStats> stats = new HashMap<List<VariableValue>, SimpleScenarioStats>();
    private List<List<VariableValue>> keys = new ArrayList<List<VariableValue>>();

    public VaryingScenarioStats() {}

    public SimpleScenarioStats next(List<VariableValue> value) {
        SimpleScenarioStats stats = new SimpleScenarioStats(1000L);
        keys.add(value);
        this.stats.put(value, stats);
        return stats;
    }

    public Map<String, Object> results() {
        Map<String, Object> map = new HashMap<String, Object>();

        List<String> dimensions = new ArrayList<String>();
        for (VariableValue keyElem : keys.get(0)) {
            dimensions.add(keyElem.getName());
        }
        map.put("dimensions", dimensions);

        Map<String, List<Object>> dimensionValues = new HashMap<String, List<Object>>();
        for (List<VariableValue> key : keys) {
            for (VariableValue elem : key) {
                List<Object> values = get(elem.getName(), dimensionValues, new ArrayList<Object>());
                String value = elem.getValue().toString();
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
        }
        map.put("dimension-values", dimensionValues);

        Map<String, Object> data = new HashMap<String, Object>();
        for (List<VariableValue> key : keys) {
            Map<String, Object> results = stats.get(key).results();
            Map<String, Object> node = data;
            for (int i = 0; i < key.size(); i++) {
                VariableValue elem = key.get(i);
                if (i == key.size() - 1) {
                    node.put(elem.getValue().toString(), results);
                }
                else {
                    node = (Map<String, Object>) get(elem.getValue().toString(), node, new HashMap<String, Object>());
                }
            }
        }
        map.put("data", data);

        return map;
    }

    private <K, V> V get(K key, Map<K, V> map, V def) {
        V val = map.get(key);
        if (val == null) {
            val = def;
            map.put(key, val);
        }
        return val;
    }
}
