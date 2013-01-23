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


package com.rabbitmq.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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

    public static void executeCommand(String command) throws IOException
    {
        Process pr = executeCommandProcess(command);

        int ev = waitForExitValue(pr);
        if (ev != 0) {
            String stdout = capture(pr.getInputStream());
            String stderr = capture(pr.getErrorStream());
            throw new IOException("unexpected command exit value: " + ev +
                                  "\nstdout:\n" + stdout +
                                  "\nstderr:\n" + stderr + "\n");
        }
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

    public static void executeCommandIgnoringErrors(String command) throws IOException
    {
        Process pr = executeCommandProcess(command);
        waitForExitValue(pr);
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

    public static void rabbitmqctl(String command) throws IOException {
        executeCommand("../rabbitmq-server/scripts/rabbitmqctl " + command);
    }

    public static void rabbitmqctlIgnoreErrors(String command) throws IOException {
        executeCommandIgnoringErrors("../rabbitmq-server/scripts/rabbitmqctl " + command);
    }
}
