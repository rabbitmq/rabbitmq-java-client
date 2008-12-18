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

package com.rabbitmq.client.test.performance;

import org.apache.commons.cli.*;

import java.util.Iterator;

/**
 * Super class for handling repetative CLI stuff
 */
public class CLIHelper {

    private Options options = new Options();

    public static CLIHelper defaultHelper() {
        Options opts = new Options(); 
        opts.addOption(new Option( "help", "print this message"));
        opts.addOption(new Option("h", "host", true, "broker host"));
        opts.addOption(new Option("p", "port", true, "broker port"));
        return new CLIHelper(opts);
    }

    public CLIHelper(Options opts) {
        Iterator it = opts.getOptions().iterator();
        while (it.hasNext()) {
            options.addOption((Option) it.next());
        }
    }

    public void addOption(Option option) {
        options.addOption(option);
    }

    public CommandLine parseCommandLine(String [] args) {
        CommandLineParser parser = new GnuParser();        
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        }
        catch (ParseException e) {
            printHelp(options);
            throw new RuntimeException("Parsing failed. Reason: " + e.getMessage());
        }

        if (commandLine.hasOption("help")) {
            printHelp(options);
            return null;
        }
        return commandLine;
    }

    public void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(getClass().getSimpleName(), options);
    }

    public static int getOptionValue(CommandLine cmd, String s, int i) {
        return Integer.parseInt(cmd.getOptionValue(s, i + ""));
    }
}
