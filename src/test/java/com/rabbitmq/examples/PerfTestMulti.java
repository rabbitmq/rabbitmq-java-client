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

package com.rabbitmq.examples;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.examples.perf.Scenario;
import com.rabbitmq.examples.perf.ScenarioFactory;
import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerfTestMulti {
    private static final ConnectionFactory factory = new ConnectionFactory();

    private static final Map<String, Object> results = new HashMap<String, Object>();

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: PerfTestMulti input-json-file output-json-file");
            System.exit(1);
        }
        String inJSON = args[0];
        String outJSON = args[1];
        List<Map> scenariosJSON = null;
        try {
            scenariosJSON = (List<Map>) new JSONReader().read(readFile(inJSON));
        } catch (FileNotFoundException e) {
            System.out.println("Input json file " + inJSON + " could not be found");
            System.exit(1);
        }
        if (scenariosJSON == null) {
            System.out.println("Input json file " + inJSON + " could not be parsed");
            System.exit(1);
        }
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
        runTests(scenarios);
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
