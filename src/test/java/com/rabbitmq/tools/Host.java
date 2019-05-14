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


package com.rabbitmq.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.NetworkConnection;
import com.rabbitmq.client.test.TestUtils;

public class Host {

    public static String capture(InputStream is)
        throws IOException
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder buff = new StringBuilder();
        while ((line = br.readLine()) != null) {
            buff.append(line).append("\n");
        }
        return buff.toString();
    }

    public static Process executeCommand(String command) throws IOException
    {
        Process pr = executeCommandProcess(command);

        int ev = waitForExitValue(pr);
        if (ev != 0) {
            String stdout = capture(pr.getInputStream());
            String stderr = capture(pr.getErrorStream());
            throw new IOException("unexpected command exit value: " + ev +
                                  "\ncommand: " + command + "\n" +
                                  "\nstdout:\n" + stdout +
                                  "\nstderr:\n" + stderr + "\n");
        }
        return pr;
    }

    private static int waitForExitValue(Process pr) {
        while(true) {
            try {
                pr.waitFor();
                break;
            } catch (InterruptedException ignored) {}
        }
        return pr.exitValue();
    }

    public static Process executeCommandIgnoringErrors(String command) throws IOException
    {
        Process pr = executeCommandProcess(command);
        waitForExitValue(pr);
        return pr;
    }

    private static Process executeCommandProcess(String command) throws IOException
    {
        String[] finalCommand;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            finalCommand = new String[4];
            finalCommand[0] = "C:\\winnt\\system32\\cmd.exe";
            finalCommand[1] = "/y";
            finalCommand[2] = "/c";
            finalCommand[3] = command;
        } else {
            finalCommand = new String[3];
            finalCommand[0] = "/bin/sh";
            finalCommand[1] = "-c";
            finalCommand[2] = command;
        }
        return Runtime.getRuntime().exec(finalCommand);
    }

    public static boolean isRabbitMqCtlCommandAvailable(String command) throws IOException {
        Process process = rabbitmqctlIgnoreErrors("");
        String stderr = capture(process.getErrorStream());
        return stderr.contains(command);
    }

    public static Process rabbitmqctl(String command) throws IOException {
        return executeCommand(rabbitmqctlCommand() +
                              " -n \'" + nodenameA() + "\'" +
                              " " + command);
    }

    public static Process rabbitmqctlIgnoreErrors(String command) throws IOException {
        return executeCommandIgnoringErrors(rabbitmqctlCommand() +
                                            " -n \'" + nodenameA() + "\'" +
                                            " " + command);
    }

    public static void setResourceAlarm(String source) throws IOException {
        rabbitmqctl("eval 'rabbit_alarm:set_alarm({{resource_limit, " + source +  ", node()}, []}).'");
    }

    public static void clearResourceAlarm(String source) throws IOException {
        rabbitmqctl("eval 'rabbit_alarm:clear_alarm({resource_limit, " + source + ", node()}).'");
    }

    public static Process invokeMakeTarget(String command) throws IOException {
        File rabbitmqctl = new File(rabbitmqctlCommand());
        return executeCommand(makeCommand() +
                              " -C \'" + rabbitmqDir() + "\'" +
                              " RABBITMQCTL=\'" + rabbitmqctl.getAbsolutePath() + "\'" +
                              " RABBITMQ_NODENAME=\'" + nodenameA() + "\'" +
                              " RABBITMQ_NODE_PORT=" + node_portA() +
                              " RABBITMQ_CONFIG_FILE=\'" + config_fileA() + "\'" +
                              " " + command);
    }

    public static void startRabbitOnNode() throws IOException {
        rabbitmqctl("start_app");
        tryConnectFor(10_000);
    }

    public static void stopRabbitOnNode() throws IOException {
        rabbitmqctl("stop_app");
    }

    public static void tryConnectFor(int timeoutInMs) throws IOException {
        tryConnectFor(timeoutInMs, node_portA() == null ? 5672 : Integer.valueOf(node_portA()));
    }

    public static void tryConnectFor(int timeoutInMs, int port) throws IOException {
        int waitTime = 100;
        int totalWaitTime = 0;
        while (totalWaitTime <= timeoutInMs) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            totalWaitTime += waitTime;
            ConnectionFactory connectionFactory = TestUtils.connectionFactory();
            connectionFactory.setPort(port);
            try (Connection ignored = connectionFactory.newConnection()) {
                return;

            } catch (Exception e) {
                // retrying
            }
        }
        throw new IOException("Could not connect to broker for " + timeoutInMs + " ms");
    }

    public static String makeCommand()
    {
        return System.getProperty("make.bin", "make");
    }

    public static String nodenameA()
    {
        return System.getProperty("test-broker.A.nodename");
    }

    public static String node_portA()
    {
        return System.getProperty("test-broker.A.node_port");
    }

    public static String config_fileA()
    {
        return System.getProperty("test-broker.A.config_file");
    }

    public static String nodenameB()
    {
        return System.getProperty("test-broker.B.nodename");
    }

    public static String node_portB()
    {
        return System.getProperty("test-broker.B.node_port");
    }

    public static String config_fileB()
    {
        return System.getProperty("test-broker.B.config_file");
    }

    public static String rabbitmqctlCommand()
    {
        return System.getProperty("rabbitmqctl.bin");
    }

    public static String rabbitmqDir()
    {
        return System.getProperty("rabbitmq.dir");
    }

    public static void closeConnection(String pid) throws IOException {
        rabbitmqctl("close_connection '" + pid + "' 'Closed via rabbitmqctl'");
    }

    public static void closeAllConnections() throws IOException {
        rabbitmqctl("close_all_connections 'Closed via rabbitmqctl'");
    }

    public static void closeConnection(NetworkConnection c) throws IOException {
        Host.ConnectionInfo ci = findConnectionInfoFor(Host.listConnections(), c);
        closeConnection(ci.getPid());
    }

    public static class ConnectionInfo {
        private final String pid;
        private final int peerPort;
        private final String clientProperties;

        public ConnectionInfo(String pid, int peerPort, String clientProperties) {
            this.pid = pid;
            this.peerPort = peerPort;
            this.clientProperties = clientProperties;
        }

        public String getPid() {
            return pid;
        }

        public int getPeerPort() {
            return peerPort;
        }

        public String getClientProperties() {
            return clientProperties;
        }

        @Override
        public String toString() {
            return "ConnectionInfo{" +
                    "pid='" + pid + '\'' +
                    ", peerPort=" + peerPort +
                    ", clientProperties='" + clientProperties + '\'' +
                    '}';
        }
    }

    public static List<ConnectionInfo> listConnections() throws IOException {
        String output = capture(rabbitmqctl("list_connections -q pid peer_port client_properties").getInputStream());
        // output (header line presence depends on broker version):
        // pid	peer_port
        // <rabbit@mercurio.1.11491.0>	58713
        String[] allLines = output.split("\n");

        ArrayList<ConnectionInfo> result = new ArrayList<ConnectionInfo>();
        for (String line : allLines) {
            if (line != null && !line.trim().isEmpty()) {
                // line: <rabbit@mercurio.1.11491.0>	58713
                String[] columns = line.split("\t");
                // can be also header line, so ignoring NumberFormatException
                try {
                    result.add(new ConnectionInfo(columns[0], Integer.valueOf(columns[1]), columns[2]));
                } catch (NumberFormatException e) {
                    // OK
                }
            }
        }
        return result;
    }

    private static Host.ConnectionInfo findConnectionInfoFor(List<Host.ConnectionInfo> xs, NetworkConnection c) {
        Host.ConnectionInfo result = null;
        for (Host.ConnectionInfo ci : xs) {
            if(c.getLocalPort() == ci.getPeerPort()){
                result = ci;
                break;
            }
        }
        return result;
    }
}
