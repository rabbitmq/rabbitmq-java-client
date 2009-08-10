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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.AMQContentHeader;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.utility.BlockingCell;

/**
 * AMQP Protocol Analyzer program. Listens on a configurable port and when a
 * connection arrives, makes an outbound connection to a configurable host and
 * port. Relays frames between each pair of sockets. Commands are decoded and
 * printed to stdout.
 */
public class Tracer implements Runnable {
    public static final boolean WITHHOLD_INBOUND_HEARTBEATS =
        new Boolean(System.getProperty("com.rabbitmq.tools.Tracer.WITHHOLD_INBOUND_HEARTBEATS"))
        .booleanValue();
    public static final boolean WITHHOLD_OUTBOUND_HEARTBEATS =
        new Boolean(System.getProperty("com.rabbitmq.tools.Tracer.WITHHOLD_OUTBOUND_HEARTBEATS"))
        .booleanValue();
    public static final boolean NO_ASSEMBLE_FRAMES =
        new Boolean(System.getProperty("com.rabbitmq.tools.Tracer.NO_ASSEMBLE_FRAMES"))
        .booleanValue();
    public static final boolean NO_DECODE_FRAMES =
        new Boolean(System.getProperty("com.rabbitmq.tools.Tracer.NO_DECODE_FRAMES"))
        .booleanValue();

    public static void main(String[] args) {
        int listenPort = args.length > 0 ? Integer.parseInt(args[0]) : 5673;
        String connectHost = args.length > 1 ? args[1] : "localhost";
        int connectPort = args.length > 2 ? Integer.parseInt(args[2]) : 5672;

        System.out.println("Usage: Tracer [<listenport> [<connecthost> [<connectport>]]]");
        System.out.println("Invoked as: Tracer " + listenPort + " " + connectHost + " " + connectPort);
        System.out.println("com.rabbitmq.tools.Tracer.WITHHOLD_INBOUND_HEARTBEATS = " +
                           com.rabbitmq.tools.Tracer.WITHHOLD_INBOUND_HEARTBEATS);
        System.out.println("com.rabbitmq.tools.Tracer.WITHHOLD_OUTBOUND_HEARTBEATS = " +
                           com.rabbitmq.tools.Tracer.WITHHOLD_OUTBOUND_HEARTBEATS);
        System.out.println("com.rabbitmq.tools.Tracer.NO_ASSEMBLE_FRAMES = " +
                           com.rabbitmq.tools.Tracer.NO_ASSEMBLE_FRAMES);
        System.out.println("com.rabbitmq.tools.Tracer.NO_DECODE_FRAMES = " +
                           com.rabbitmq.tools.Tracer.NO_DECODE_FRAMES);

        try {
            ServerSocket server = new ServerSocket(listenPort);
            int counter = 0;
            while (true) {
                Socket conn = server.accept();
                new Tracer(conn, counter++, connectHost, connectPort);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
    }

    public Socket inSock;

    public Socket outSock;

    public int id;

    public DataInputStream iis;

    public DataOutputStream ios;

    public DataInputStream ois;

    public DataOutputStream oos;

    public Tracer(Socket sock, int id, String host, int port) throws IOException {
        this.inSock = sock;
        this.outSock = new Socket(host, port);
        this.id = id;

        this.iis = new DataInputStream(inSock.getInputStream());
        this.ios = new DataOutputStream(inSock.getOutputStream());
        this.ois = new DataInputStream(outSock.getInputStream());
        this.oos = new DataOutputStream(outSock.getOutputStream());

        new Thread(this).start();
    }

    public void run() {
        try {
            byte[] handshake = new byte[8];
            iis.readFully(handshake);
            oos.write(handshake);

            BlockingCell<Object> w = new BlockingCell<Object>();
            DirectionHandler inHandler = new DirectionHandler(w, true, iis, oos);
            DirectionHandler outHandler = new DirectionHandler(w, false, ois, ios);
            new Thread(inHandler).start();
            new Thread(outHandler).start();
            Object result = w.uninterruptibleGet();
            if (result instanceof Exception) {
                ((Exception) result).printStackTrace();
            }
        } catch (EOFException eofe) {
            eofe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                inSock.close();
                outSock.close();
            } catch (IOException ioe2) {
                ioe2.printStackTrace();
            }
        }
    }

    public class DirectionHandler implements Runnable {
        public BlockingCell<Object> waitCell;

        public boolean inBound;

        public DataInputStream i;

        public DataOutputStream o;

        public AMQCommand.Assembler c;

        public DirectionHandler(BlockingCell<Object> waitCell, boolean inBound, DataInputStream i, DataOutputStream o) {
            this.waitCell = waitCell;
            this.inBound = inBound;
            this.i = i;
            this.o = o;
            this.c = AMQCommand.newAssembler();
        }

        public Frame readFrame() throws IOException {
            return Frame.readFrom(i);
        }

        public void report(int channel, Object object) {
            System.out.println("" + System.currentTimeMillis() + ": conn#" + id + " ch#" + channel + (inBound ? " -> " : " <- ") + object);
        }

        public void reportFrame(Frame f)
            throws IOException
        {
            switch (f.type) {
              case AMQP.FRAME_METHOD: {
                  report(f.channel, AMQImpl.readMethodFrom(f.getInputStream()));
                  break;
              }
              case AMQP.FRAME_HEADER: {
                  DataInputStream in = f.getInputStream();
                  AMQContentHeader contentHeader = AMQImpl.readContentHeaderFrom(in);
                  long remainingBodyBytes = contentHeader.readFrom(in);
                  report(f.channel,
                         "Expected body size: " + remainingBodyBytes +
                         "; " + contentHeader.toString());
                  break;
              }
              default: {
                  report(f.channel, f);
              }
            }
        }

        public void doFrame() throws IOException {
            Frame f = readFrame();
            if (f != null) {
                if (f.type == AMQP.FRAME_HEARTBEAT) {
                    if ((inBound && !WITHHOLD_INBOUND_HEARTBEATS) ||
                        (!inBound && !WITHHOLD_OUTBOUND_HEARTBEATS))
                    {
                        f.writeTo(o);
                        report(f.channel, f);
                    } else {
                        report(f.channel, "(withheld) " + f.toString());
                    }
                } else {
                    f.writeTo(o);
                    if (NO_ASSEMBLE_FRAMES || NO_DECODE_FRAMES) {
                        if (NO_DECODE_FRAMES) {
                            report(f.channel, f);
                        } else {
                            reportFrame(f);
                        }
                    } else {
                        AMQCommand cmd = c.handleFrame(f);
                        if (cmd != null) {
                            report(f.channel, cmd);
                            c = AMQCommand.newAssembler();
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
                waitCell.setIfUnset(e);
            } finally {
                waitCell.setIfUnset(new Object());
            }
        }
    }
}
