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

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerformanceMain {
    private static final ConnectionFactory factory = new ConnectionFactory();

    private static Map<String, Object> results = new HashMap<String, Object>();

    public static void main(String[] args) throws Exception {
        String inJSON = args[0];
        String outJSON = args[1];
        List<Map> scenariosJSON = (List<Map>) new JSONReader().read(readFile(inJSON));
        Scenario[] scenarios = new Scenario[scenariosJSON.size()];
        for (int i = 0; i < scenariosJSON.size(); i++) {
            scenarios[i] = ScenarioFactory.fromJSON(scenariosJSON.get(i), factory);
        }
        runStaticBrokerTests(scenarios);
        writeJSON(outJSON);
    }

    private static String readFile(String path) throws IOException {
        final char[] buf = new char[4096];
        StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(new FileInputStream(path), "UTF-8");
        try {
            int chars;
            while ((chars = in.read(buf, 0, buf.length)) > 0) {
                out.append(buf, 0, chars);
            }
        } finally {
            in.close();
        }
        return out.toString();
    }

    private static void writeJSON(String outJSON) throws IOException {
        FileWriter outFile = new FileWriter(outJSON);
        PrintWriter out = new PrintWriter(outFile);
        out.println(new JSONWriter(true).write(results));
        outFile.close();
    }

    private static void runStaticBrokerTests(Scenario[] scenarios) throws Exception {
        Broker broker = Broker.DEFAULT;
        broker.start();
        runTests(scenarios);
        broker.stop();
    }

    private static void runTests(Scenario[] scenarios) throws Exception {
        for (Scenario scenario : scenarios) {
            System.out.print("Running scenario '" + scenario.getName() + "' ");
            scenario.run();
            System.out.println();
            results.put(scenario.getName(), scenario.getStats().results());
        }
    }
}