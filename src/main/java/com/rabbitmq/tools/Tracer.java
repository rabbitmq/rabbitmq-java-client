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
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.tools;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.AMQContentHeader;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.utility.BlockingCell;
import com.rabbitmq.utility.Utility;

/**
 * AMQP Protocol Analyzer program. Listens on a port (in-port) and when a
 * connection arrives, makes an outbound connection to a host and
 * port (out-port). Relays frames from the in-port to the out-port.
 * Commands are decoded and printed to a supplied {@link Logger}.
 * <p/>
 * The stand-alone program ({@link #main(String[])}) prints to <code>System.out</code>,
 * using a private {@link AsyncLogger} instance.  When the connection closes
 * the program listens for a subsequent connection and traces that to the same {@link Logger}.
 * This continues until the program is interrupted.
 * <p/>
 * Options for controlling, for example, whether command bodies are decoded,
 * are obtained from <code>System.properties</code>, and are reported to the console
 * before starting the trace.
 * <p/>
 * A {@link Tracer} object may be instantiated, using one of the constructors
 * <ul>
 * <li><code>Tracer(int listenPort, String id, String host, int port, Logger logger, Properties props)</code></li>
 * <li><code>Tracer(String id)</code></li>
 * <li><code>Tracer(String id, Properties props)</code>
 * <p/>where the missing parameters default as follows:
 * <ul>
 * <li><code>listenPort</code> defaults to <code>5673</code></li>
 * <li><code>host</code> defaults to <q><code>localhost</code></q></li>
 * <li><code>port</code> defaults to <code>5672</code></li>
 * <li><code>logger</code> defaults to <code>new AsyncLogger(System.out)</code></li> and
 * <li><code>props</code> defaults to <code>System.getProperties()</code></li>
 * </ul>
 * </li>
 * </ul>
 * <p/>
 * These constructors block waiting for a connection to arrive on the listenPort.
 * Tracing does not begin until the tracer is {@link #start()}ed which {@link Logger#start()}s
 * the supplied logger and creates and starts a {@link Thread} for relaying and deconstructing the frames.
 * <p/>
 * The properties specified in <code>props</code> are used at {@link Tracer#start()} time and may be modified
 * before this call.
 * @see Tracer.Logger
 * @see Tracer.AsyncLogger
 */
public class Tracer implements Runnable {
    private static final int DEFAULT_LISTEN_PORT = 5673;
    private static final String DEFAULT_CONNECT_HOST = "localhost";
    private static final int DEFAULT_CONNECT_PORT = 5672;

    private static final String PROP_PREFIX = "com.rabbitmq.tools.Tracer.";

    private static boolean getBoolProperty(String propertyName, Properties props) {
        return Boolean.parseBoolean(props.getProperty(PROP_PREFIX + propertyName));
    }

    private static void printBoolProperty(String propName, Properties props) {
        StringBuilder sb = new StringBuilder(100);
        System.out.println(sb.append(PROP_PREFIX).append(propName)
                .append(" = ").append(getBoolProperty(propName, props)).toString());
    }

    public static void main(String[] args) {
        int listenPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_LISTEN_PORT;
        String connectHost = args.length > 1 ? args[1] : DEFAULT_CONNECT_HOST;
        int connectPort = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_CONNECT_PORT;

        System.out.println("Usage: Tracer [<listenport> [<connecthost> [<connectport>]]]");
        System.out.println("   Serially traces connections on the <listenport>, logging\n"
                         + "   frames received and passing them to the connect host and port.");
        System.out.println("Invoked as: Tracer " + listenPort + " " + connectHost + " " + connectPort);

        Properties props = System.getProperties();
        printBoolProperty("WITHHOLD_INBOUND_HEARTBEATS", props);
        printBoolProperty("WITHHOLD_OUTBOUND_HEARTBEATS", props);
        printBoolProperty("NO_ASSEMBLE_FRAMES", props);
        printBoolProperty("NO_DECODE_FRAMES", props);
        printBoolProperty("SUPPRESS_COMMAND_BODIES", props);

        Logger logger = new AsyncLogger(System.out); // initially stopped
        try {
            ServerSocket server = new ServerSocket(listenPort);
            int counter = 0;
            while (true) {
                Socket conn = server.accept();
                new Tracer( conn
                          , "Tracer-" + (counter++)
                          , connectHost, connectPort
                          , logger
                          ).start();
            }
        } catch (Exception e) {
            logger.stop(); // will stop shared logger thread
            e.printStackTrace();
            System.exit(1);
        }
    }

    private final Properties props;
    private final Socket inSock;
    private final Socket outSock;
    private final String idLabel;
    private final DataInputStream iis;
    private final DataOutputStream ios;
    private final DataInputStream ois;
    private final DataOutputStream oos;
    private final Logger logger;
    private final BlockingCell<Exception> reportEnd;
    private final AtomicBoolean started;

    private Tracer(Socket sock, String id, String host, int port, Logger logger, BlockingCell<Exception> reportEnd, Properties props)
            throws IOException {

        this.props = props;
        this.inSock = sock;
        this.outSock = new Socket(host, port);
        this.idLabel = ": <" + id + "> ";

        this.iis = new DataInputStream(this.inSock.getInputStream());
        this.ios = new DataOutputStream(this.inSock.getOutputStream());
        this.ois = new DataInputStream(this.outSock.getInputStream());
        this.oos = new DataOutputStream(this.outSock.getOutputStream());
        this.logger = logger;
        this.reportEnd = reportEnd;
        this.started = new AtomicBoolean(false);
    }

    private Tracer(Socket sock, String id, String host, int port, Logger logger)
            throws IOException {
        this(sock, id, host, port, logger, new BlockingCell<Exception>(), System.getProperties());
    }

    private Tracer(int listenPort, String id, String host, int port,
            Logger logger, BlockingCell<Exception> reportEnd, Properties props)
            throws IOException {
        this(new ServerSocket(listenPort).accept(), id, host, port, logger, reportEnd, props);
    }

    public Tracer(int listenPort, String id, String host, int port, Logger logger, Properties props) throws IOException {
        this(listenPort, id, host, port, logger, new BlockingCell<Exception>(), props);
    }

    public Tracer(String id) throws IOException {
        this(DEFAULT_LISTEN_PORT, id, DEFAULT_CONNECT_HOST, DEFAULT_CONNECT_PORT, new AsyncLogger(System.out), new BlockingCell<Exception>(), System.getProperties());
    }

    public Tracer(String id, Properties props) throws IOException {
        this(DEFAULT_LISTEN_PORT, id, DEFAULT_CONNECT_HOST, DEFAULT_CONNECT_PORT, new AsyncLogger(System.out), new BlockingCell<Exception>(), props);
    }

    public void start() {
        if (this.started.compareAndSet(false, true)) {
            this.logger.start();
            new Thread(this).start();
        }
    }

    public void run() {
        try {
            byte[] handshake = new byte[8];
            this.iis.readFully(handshake);
            this.oos.write(handshake);

            BlockingCell<Exception> wio = new BlockingCell<Exception>();

            new Thread(new DirectionHandler(wio, true, this.iis, this.oos, this.props))
                    .start();
            new Thread(new DirectionHandler(wio, false, this.ois, this.ios, this.props))
                    .start();

            waitAndLogException(wio); // either stops => we stop
        } catch (Exception e) {
            reportAndLogNonNullException(e);
        } finally {
            try { this.inSock.close(); } catch (Exception e) { logException(e); }
            try { this.outSock.close(); } catch (Exception e) { logException(e); }
            this.reportEnd.setIfUnset(null);
            this.logger.stop();
        }
    }

    private void waitAndLogException(BlockingCell<Exception> bc) throws InterruptedException {
        reportAndLogNonNullException(bc.get());
    }

    private void reportAndLogNonNullException(Exception e) {
        if (e!=null) {
            this.reportEnd.setIfUnset(e);
            logException(e);
        }
    }

    public void log(String message) {
        StringBuilder sb = new StringBuilder();
        this.logger.log(sb.append(System.currentTimeMillis())
                          .append(this.idLabel)
                          .append(message)
                        .toString());
    }

    public void logException(Exception e) {
        log("uncaught " + Utility.makeStackTrace(e));
    }

    private class DirectionHandler implements Runnable {
        private final BlockingCell<Exception> waitCell;
        private final boolean silentMode;
        private final boolean noDecodeFrames;
        private final boolean noAssembleFrames;
        private final boolean suppressCommandBodies;
        private final boolean writeHeartBeats;
        private final String directionIndicator;
        private final DataInputStream inStream;
        private final DataOutputStream outStream;
        private final Map<Integer, AMQCommand> commands;

        public DirectionHandler(BlockingCell<Exception> waitCell, boolean inBound,
                DataInputStream inStream, DataOutputStream outStream, Properties props) {
            this.waitCell = waitCell;
            this.silentMode = getBoolProperty("SILENT_MODE", props);
            this.noDecodeFrames = getBoolProperty("NO_DECODE_FRAMES", props);
            this.noAssembleFrames = getBoolProperty("NO_ASSEMBLE_FRAMES", props);
            this.suppressCommandBodies = getBoolProperty("SUPPRESS_COMMAND_BODIES", props);
            this.writeHeartBeats
               = ( inBound && !getBoolProperty("WITHHOLD_INBOUND_HEARTBEATS", props))
              || (!inBound && !getBoolProperty("WITHHOLD_OUTBOUND_HEARTBEATS", props));
            this.directionIndicator = (inBound ? " -> " : " <- ");
            this.inStream = inStream;
            this.outStream = outStream;
            this.commands = new HashMap<Integer, AMQCommand>();
        }

        private Frame readFrame() throws IOException {
            return Frame.readFrom(this.inStream);
        }

        private void report(int channel, Object object) {
            StringBuilder sb = new StringBuilder("ch#").append(channel)
                    .append(this.directionIndicator).append(object);
            Tracer.this.log(sb.toString());
        }

        private void reportFrame(Frame frame) throws IOException {
            switch (frame.type) {

            case AMQP.FRAME_METHOD: {
                report(frame.channel, AMQImpl.readMethodFrom(frame.getInputStream()));
                break;
            }

            case AMQP.FRAME_HEADER: {
                AMQContentHeader contentHeader = AMQImpl
                        .readContentHeaderFrom(frame.getInputStream());
                long remainingBodyBytes = contentHeader.getBodySize();
                StringBuilder sb = new StringBuilder("Expected body size: ")
                        .append(remainingBodyBytes).append("; ")
                        .append(contentHeader);
                report(frame.channel, sb);
                break;
            }

            default:
                report(frame.channel, frame);
            }
        }

        private void doFrame() throws IOException {
            Frame frame = readFrame();

            if (frame != null) {
                if (this.silentMode) {
                    frame.writeTo(this.outStream);
                    return;
                }
                if (frame.type == AMQP.FRAME_HEARTBEAT) {
                    if (this.writeHeartBeats) {
                        frame.writeTo(this.outStream);
                        report(frame.channel, frame);
                    } else {
                        report(frame.channel, "(withheld) " + frame.toString());
                    }
                } else {
                    frame.writeTo(this.outStream);
                    if (this.noDecodeFrames) {
                        report(frame.channel, frame);
                    } else if (this.noAssembleFrames) {
                        reportFrame(frame);
                    } else {
                        AMQCommand cmd = this.commands.get(frame.channel);
                        if (cmd == null) {
                            cmd = new AMQCommand();
                            this.commands.put(frame.channel, cmd);
                        }
                        if (cmd.handleFrame(frame)) {
                            report(frame.channel, cmd.toString(this.suppressCommandBodies));
                            commands.remove(frame.channel);
                        }
                    }
                }
            }
        }

        public void run() {
            try {
                while (true) {
                    doFrame();
                }
            } catch (Exception e) {
                this.waitCell.setIfUnset(e);
            } finally {
                this.waitCell.setIfUnset(null);
            }
        }
    }

    /**
     * Logging strings to an outputStream. Logging may be started and stopped.
     */
    public interface Logger {
        /**
         * Start logging, that is, printing log entries written using
         * {@link #log(String)}. Multiple successive starts are equivalent to a
         * single start.
         * 
         * @return <code>true</code> if start actually started the logger;
         *         <code>false</code> otherwise.
         */
        boolean start();

        /**
         * Stop logging, that is, stop printing log entries written using
         * {@link #log(String)}. Flush preceding writes. The logger can only be
         * stopped if started. Multiple successive stops are equivalent to a
         * single stop.
         * 
         * @return <code>true</code> if stop actually stopped the logger;
         *         <code>false</code> otherwise.
         */
        boolean stop();

        /**
         * Write msg to the log. This may block, and may block indefinitely if
         * the logger is stopped.
         * 
         * @param msg
         */
        void log(String msg);
    }

    /**
     * A {@link Tracer.Logger} designed to print {@link String}s to a designated {@link OutputStream}
     * on a private thread.
     * <p/>{@link String}s are read from a private queue and <i>printed</i> to a buffered {@link PrintStream}
     * which is periodically flushed.
     * <p/>
     * When instantiated the private queue is created (an in-memory {@link ArrayBlockingQueue} in this
     * implementation) and when {@link #start()}ed the private thread is created and started unless it is
     * already present.  An {@link AsyncLogger} may be started many times, but only one thread is created.
     * <p/>
     * When {@link #stop()}ed either the number of starts is decremented, or, if this count reaches zero,
     * a special element is queued which causes the private thread to end when encountered.
     * <p/>
     * If the private thread is interrupted, the thread will also end, and the count set to zero,
     * This will cause subsequent {@link #stop()}s to be ignored, and the next {@link #start()} will create a new thread.
     * <p/>
     * {@link #log(String)} never blocks unless the private queue is full; this may never un-block if the {@link Logger} is stopped.
     */
    public static class AsyncLogger implements Logger {
        private static final int MIN_FLUSH_INTERVAL = 100;
        private static final int ONE_SECOND_INTERVAL = 1000;
        private static final int LOG_QUEUE_SIZE = 1024 * 1024;
        private static final int BUFFER_SIZE = 10 * 1024 * 1024;

        private final Runnable loggerRunnable;

        private final SafeCounter countStarted;
        private volatile Thread loggerThread = null;

        /**
         * Simple pair class for queue elements.
         * @param <L> type of left item
         * @param <R> type of right item
         */
        private static class Pr<L,R> {
            private final L left;
            private final R right;
            public L left() { return this.left; }
            public R right() { return this.right; }
            public Pr(L left, R right) { this.left=left; this.right=right; }
        }

        private enum LogCmd {
            STOP,
            PRINT
        }

        private final BlockingQueue<Pr<String, LogCmd> > queue = new ArrayBlockingQueue<Pr<String, LogCmd> >(
                LOG_QUEUE_SIZE, true);

        /**
         * Same as {@link #Tracer.AsyncLogger(OutputStream, int)} with a one-second flush interval.
         * @param os OutputStream to print to.
         */
        public AsyncLogger(OutputStream os) {
            this(os, ONE_SECOND_INTERVAL);
        }

        /**
         * Start/stoppable logger that prints to an {@link OutputStream} with flushes every <code>flushInterval</code> milliseconds.
         * @param os OutputStream to print to.
         * @param flushInterval in milliseconds, time between flushes.
         */
        public AsyncLogger(OutputStream os, int flushInterval) {
            if (flushInterval < MIN_FLUSH_INTERVAL)
                throw new IllegalArgumentException("Flush interval ("
                        + flushInterval + "ms) must be positive and at least "
                        + MIN_FLUSH_INTERVAL + "ms.");
            this.countStarted = new SafeCounter();

            PrintStream printStream = new PrintStream(new BufferedOutputStream(
                    os, BUFFER_SIZE), false);
            this.loggerRunnable = new AsyncLoggerRunnable(printStream,
                    flushInterval, this.queue);
        }

        public void log(String message) {
            if (message != null) {
                try {
                    this.queue.put(new Pr<String, LogCmd>(message, LogCmd.PRINT));
                } catch (InterruptedException ie) {
                    throw new RuntimeException("Interrupted while logging.", ie);
                }
            }
        }

        public boolean start() {
            if (this.countStarted.testZeroAndIncrement()) {
                this.loggerThread = new Thread(this.loggerRunnable);
                this.loggerThread.start();
                return true;
            }
            return false; // meaning already started
        }

        public boolean stop() {
            if (this.countStarted.decrementAndTestZero()) {
                if (this.loggerThread != null) {
                    try {
                        this.queue.put(new Pr<String, LogCmd>(null, LogCmd.STOP));
                    } catch (InterruptedException ie) {
                        this.loggerThread.interrupt();  //try harder
                        throw new RuntimeException("Interrupted while stopping.", ie);
                    }
                    this.loggerThread = null;
                }
                return true;
            }
            return false; // meaning already stopped
        }

        private class AsyncLoggerRunnable implements Runnable {
            private final int flushInterval;
            private final PrintStream ps;
            private final BlockingQueue<Pr<String, LogCmd> > queue;

            public AsyncLoggerRunnable(PrintStream ps, int flushInterval,
                    BlockingQueue<Pr<String, LogCmd> > queue) {
                this.flushInterval = flushInterval;
                this.ps = ps;
                this.queue = queue;
            }

            public void run() {
                try {
                    long timeOfNextFlush = System.currentTimeMillis()
                            + this.flushInterval;
                    boolean printedSinceLastFlush = false;
                    while (true) {
                        long timeToNextFlush;
                        while (0 >= (timeToNextFlush = timeOfNextFlush
                                - System.currentTimeMillis())) {
                            if (printedSinceLastFlush) {
                                this.ps.flush();
                                printedSinceLastFlush = false;
                            }
                            timeOfNextFlush += this.flushInterval;
                        }
                        Pr<String, LogCmd> item = this.queue.poll(timeToNextFlush,
                                TimeUnit.MILLISECONDS);
                        if (item != null) {
                            if (item.left() != null) {
                                this.ps.println(item.left());
                                printedSinceLastFlush = true;
                            }
                            if (item.right() == LogCmd.STOP) break;
                        }
                    }
                    drainCurrentQueue();
                    this.ps.println("Stopped.");
                    this.ps.flush();

                } catch (InterruptedException ie) {
                    AsyncLogger.this.countStarted.reset();
                    drainCurrentQueue();
                    this.ps.println("Interrupted.");
                    this.ps.flush();
                }
            }

            private void drainCurrentQueue() {
                int currentSize = this.queue.size();
                while (currentSize-- > 0) {
                    Pr<String, LogCmd> item = this.queue.poll();
                    if (item != null && item.left() != null)
                        this.ps.println(item.left());
                }
            }
        }
    }
    
    private static class SafeCounter {
        private final Object countMonitor = new Object();
        private int count;
        public SafeCounter() {
            this.count = 0;
        }
        public boolean testZeroAndIncrement() {
            synchronized (this.countMonitor) {
                int val = this.count;
                this.count++;
                return (val == 0);
            }
        }
        public boolean decrementAndTestZero() {
            synchronized (this.countMonitor) {
                if (this.count == 0) return false;
                --this.count;
                return (0 == this.count);
            }
        }
        public void reset() {
            synchronized (this.countMonitor) {
                this.count = 0;
            }
        }
    }
}
