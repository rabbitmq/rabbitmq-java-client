package com.rabbitmq.examples.perf;

import com.rabbitmq.client.ConnectionFactory;

import java.util.List;
import java.util.Map;

public class ScenarioFactory {
    public static Scenario fromJSON(Map json, ConnectionFactory factory) {
        String type = read("type", json, String.class);
        String name = read("name", json, String.class);
        Integer interval = read("interval", json, Integer.class, 1000);
        List paramsJSON = read("params", json, List.class);

        MulticastParams[] params = new MulticastParams[paramsJSON.size()];
        for (int i = 0; i < paramsJSON.size(); i++) {
            params[i] = paramsFromJSON((Map) paramsJSON.get(i));
        }

        if (type.equals("simple")) {
            return new SimpleScenario(name, factory, interval, params);
        }
        else if (type.equals("rate-vs-latency")) {
            return new RateVsLatencyScenario(name, factory, params[0]); // TODO
        }
        else if (type.equals("varying")) {
            List variablesJSON = read("variables", json, List.class);
            Variable[] variables = new Variable[variablesJSON.size()];
            for (int i = 0; i < variablesJSON.size(); i++) {
                variables[i] = variableFromJSON((Map) variablesJSON.get(i));
            }

            return new VaryingScenario(name, factory, params, variables);
        }

        throw new RuntimeException("Type " + type + " was not simple or varying.");
    }

    private static<T> T read(String key, Map map, Class<T> clazz) {
        if (map.containsKey(key)) {
            return read0(key, map, clazz);
        }
        else {
            throw new RuntimeException("Key " + key + " not found.");
        }
    }

    private static<T> T read(String key, Map map, Class<T> clazz, T def) {
        if (map.containsKey(key)) {
            return read0(key, map, clazz);
        }
        else {
            return def;
        }
    }

    private static <T> T read0(String key, Map map, Class<T> clazz) {
        Object o = map.get(key);
        if (clazz.isAssignableFrom(o.getClass())) {
            return (T) o;
        }
        else {
            throw new RuntimeException("Object under key " + key + " was a " + o.getClass() + ", not a " + clazz + ".");
        }
    }

    private static MulticastParams paramsFromJSON(Map json) {
        MulticastParams params = new MulticastParams();
        for (Object key : json.keySet()) {
            PerfUtil.setValue(params, hyphensToCamel((String)key), json.get(key));
        }
        return params;
    }

    private static Variable variableFromJSON(Map json) {
        String type = read("type", json, String.class, "multicast");
        String name = read("name", json, String.class);
        Object[] values = read("values", json, List.class).toArray();

        if (type.equals("multicast")) {
            return new MulticastVariable(hyphensToCamel(name), values);
        }

        throw new RuntimeException("Type " + type + " was not multicast");
    }

    private static String hyphensToCamel(String name) {
        String out = "";
        for (String part : name.split("-")) {
            out += part.substring(0, 1).toUpperCase() + part.substring(1);
        }
        return out.substring(0, 1).toLowerCase() + out.substring(1);
    }
}
