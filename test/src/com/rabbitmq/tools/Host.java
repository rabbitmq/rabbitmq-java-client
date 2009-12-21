//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
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
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        final StringBuffer buff = new StringBuffer();
        while ((line = br.readLine()) != null) {
            buff.append(line);
        }
        return buff.toString();
    }

    public static void executeCommand(String command)
        throws IOException
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
        final Process pr = Runtime.getRuntime().exec(finalCommand);
        final String stdout = capture(pr.getInputStream());
        final String stderr = capture(pr.getErrorStream());
        while(true) {
            try {
                pr.waitFor();
                break;
            } catch (InterruptedException e) {}
        }
        int ev = pr.exitValue();
        if (ev != 0) {
            throw new IOException("unexpected command exit value: " + ev +
                                  "\nstdout:\n" + stdout +
                                  "\nstderr:\n" + stderr + "\n");
        }
    }
}
