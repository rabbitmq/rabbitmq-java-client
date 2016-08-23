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
 * Super class for handling repetative CLI stuff
 */
public class CLIHelper {

    private final Options options = new Options();

    public static CLIHelper defaultHelper() {
        Options opts = new Options();
        opts.addOption(new Option( "help", "print this message"));
        opts.addOption(new Option("h", "host", true, "broker host"));
        opts.addOption(new Option("p", "port", true, "broker port"));
        return new CLIHelper(opts);
    }

    public CLIHelper(Options opts) {
        Iterator<?> it = opts.getOptions().iterator();
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
