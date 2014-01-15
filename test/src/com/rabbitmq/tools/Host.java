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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2013 GoPivotal, Inc.  All rights reserved.
//


package com.rabbitmq.tools;

import com.rabbitmq.client.impl.NetworkConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Host {

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

    private static String captureWithNewlines(InputStream is)
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
            } catch (InterruptedException e) {}
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
        if (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
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

    public static Process rabbitmqctl(String command) throws IOException {
        return executeCommand("../rabbitmq-server/scripts/rabbitmqctl " + command);
    }

    public static Process rabbitmqctlIgnoreErrors(String command) throws IOException {
        return executeCommandIgnoringErrors("../rabbitmq-server/scripts/rabbitmqctl " + command);
    }

    public static void closeConnection(String pid) throws IOException {
        rabbitmqctl("close_connection '" + pid + "' 'Closed via rabbitmqctl'");
    }

    public static void closeConnection(NetworkConnection c) throws IOException {
        Host.ConnectionInfo ci = findConnectionInfoFor(Host.listConnections(), c);
        closeConnection(ci.getPid());
    }

    public static class ConnectionInfo {
        private final String pid;
        private final String name;

        public ConnectionInfo(String pid, String name) {
            this.pid = pid;
            this.name = name;
        }

        public String getPid() {
            return pid;
        }

        public String getName() {
            return name;
        }
    }

    public static List<ConnectionInfo> listConnections() throws IOException {
        String output = captureWithNewlines(rabbitmqctl("list_connections pid name").getInputStream());
        String[] allLines = output.split("\n");
        Pattern pattern = Pattern.compile("(<.+\\.\\d+\\.\\d+\\.\\d+>)\\s+(.+)");

        ArrayList<ConnectionInfo> result = new ArrayList<ConnectionInfo>();
        for (String line : Arrays.copyOfRange(allLines, 1, allLines.length - 1)) {
            Matcher m = pattern.matcher(line);
            m.find();
            result.add(new ConnectionInfo(m.group(1), m.group(2)));
        }
        return result;
    }

    private static Host.ConnectionInfo findConnectionInfoFor(List<Host.ConnectionInfo> xs, NetworkConnection c) {
        Host.ConnectionInfo result = null;
        for (Host.ConnectionInfo ci : xs) {
            if(c.getName().equals(ci.getName())){
                result = ci;
                break;
            }
        }
        return result;
    }
}
