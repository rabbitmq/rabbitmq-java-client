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

import com.rabbitmq.tools.Host;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class Broker {
    private static final String BASE = "/tmp/rabbitmq-performance/";
    private static final String SCRIPTS = "../rabbitmq-server/scripts/";

    private static final String HIPE_C = "{rabbit, [{hipe_compile, true}]}";
    private static final String COARSE_C = "{rabbitmq_management_agent, [{force_fine_statistics, false}]}";

    public static final Broker DEFAULT = new Broker("default");
    public static final Broker HIPE = new Broker("hipe", "[" + HIPE_C + "].");
    public static final Broker COARSE = new Broker("coarse", "[" + COARSE_C + "].");
    public static final Broker HIPE_COARSE = new Broker("hipe_coarse", "[" + HIPE_C + "," + COARSE_C + "].");

    private final String name;
    private final String config;

    public Broker(String name) {
        this(name, "[].");
    }

    public Broker(String name, String config) {
        this.name = name;
        this.config = config;
    }

    public void start() throws IOException {
        Process pr = null;
        try {
            writeConfig();

            System.out.println("Starting broker '" + name + "'...");
            ProcessBuilder pb = new ProcessBuilder(SCRIPTS + "rabbitmq-server");
            pb.environment().put("RABBITMQ_PID_FILE", pidfile());
            pb.environment().put("RABBITMQ_LOG_BASE", BASE + "logs");
            pb.environment().put("RABBITMQ_MNESIA_DIR", BASE + "db");
            pb.environment().put("RABBITMQ_PLUGINS_EXPAND_DIR", BASE + "plugins-expand");
            pb.environment().put("RABBITMQ_CONFIG_FILE", BASE + "rabbitmq");

            pr = pb.start();

            Host.executeCommand(SCRIPTS + "rabbitmqctl wait " + pidfile());

        } catch (IOException e) {
            System.out.println("Broker start failed!");
            String stdout = capture(pr.getInputStream());
            String stderr = capture(pr.getErrorStream());
            System.out.println(stdout);
            System.out.println(stderr);
            throw new RuntimeException(e);
        }
    }

    private String pidfile() {
        return BASE + "pid";
    }

    private void writeConfig() throws IOException {
        new File(BASE).mkdirs();
        FileWriter outFile = new FileWriter(BASE + "rabbitmq.config");
        PrintWriter out = new PrintWriter(outFile);
        out.println(config);
        outFile.close();
    }

    public void stop() {
        System.out.println("Stopping broker '" + name + "' ...");
        try {
            Host.executeCommand(SCRIPTS + "rabbitmqctl stop " + pidfile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }


    private static String capture(InputStream is)
        throws IOException
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder buff = new StringBuilder();
        while ((line = br.readLine()) != null) {
            buff.append(line);
        }
        return buff.toString();
    }
}
