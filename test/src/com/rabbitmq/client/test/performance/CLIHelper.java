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
