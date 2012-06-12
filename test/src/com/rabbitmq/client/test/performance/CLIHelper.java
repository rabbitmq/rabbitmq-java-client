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

package com.rabbitmq.client.test.performance;

import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Super class for handling repetitive CLI stuff
 */
public class CLIHelper {

    private Options options = new Options();

    /**
     * @return the default helper
     */
    public static CLIHelper defaultHelper() {
        Options opts = new Options();
        opts.addOption(new Option( "help", "print this message"));
        opts.addOption(new Option("h", "host", true, "broker host"));
        opts.addOption(new Option("p", "port", true, "broker port"));
        return new CLIHelper(opts);
    }

    /**
     * @param opts options
     */
    public CLIHelper(Options opts) {
        Iterator<?> it = opts.getOptions().iterator();
        while (it.hasNext()) {
            options.addOption((Option) it.next());
        }
    }

    /**
     * @param option to add
     */
    public void addOption(Option option) {
        options.addOption(option);
    }

    /**
     * @param args array of argument strings
     * @return a parsed CommandLine
     */
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

    private void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(getClass().getSimpleName(), options);
    }

    /**
     * @param cmd parsed command line
     * @param s option key string
     * @param i default (int) value
     * @return int option value
     */
    public static int getOptionValue(CommandLine cmd, String s, int i) {
        return Integer.parseInt(cmd.getOptionValue(s, i + ""));
    }
}
