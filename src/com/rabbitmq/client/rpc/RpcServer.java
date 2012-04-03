// The contents of this file are subject to the Mozilla Public License
// Version 1.1 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License
// at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS"
// basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
// the License for the specific language governing rights and
// limitations under the License.
//
// The Original Code is RabbitMQ.
//
// The Initial Developer of the Original Code is VMware, Inc.
// Copyright (c) 2011 VMware, Inc.  All rights reserved.
//
package com.rabbitmq.client.rpc;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.rabbitmq.client.Channel;

/**
 * An {@link RpcServer} starts {@link RpcProcessor}s on a particular {@link Channel}. It keeps track
 * of them so that they may all be stopped when the server is closed. All the processors share the
 * same channel.
 * <p/>
 * An iterator is provided so that it is possible to iterate over the processors in this server by
 * using <i>foreach</i>: <code><b>for</b> ( RpcProcessor proc : rpcServer ) {...}</code>.
 */
public class RpcServer implements Iterable<RpcProcessor> {

    private final Channel channel;
    /** Protect <code>processors</code> elements and <code>closed</code>. */
    private final Object monitor = new Object();
    private final Set<RpcProcessor> processors;
    private volatile boolean closed = false;

    private RpcServer(Channel channel) {
        this.channel = channel;
        this.processors = new HashSet<RpcProcessor>();
    }

    /**
     * Create a new {@link RpcServer} which manages {@link RpcProcessor}s working on a channel.
     * @param channel that {@link RpcProcessor}s are attached to.
     * @return a new RpcServer with no processors.
     */
    public static RpcServer newServer(Channel channel) {
        return new RpcServer(channel);
    }

    /**
     * Add a {@link RpcProcessor} to this server and start it on the server's channel.
     * @param processor to add
     * @throws IOException if the processor (object) is already in this server
     */
    public void startProcessor(RpcProcessor processor) throws IOException {
        synchronized (this.monitor) {
            if (this.closed) {
                throw new IOException("RpcServer is already closed.");
            }
            if (this.processors.contains(processor))
                throw new IOException("RpcProcessor already in RpcServer.");
            this.processors.add(processor);
            processor.start(this.channel);
        }
    }

    /**
     * Stop all the processors on this server and remove them from the server. After the server is
     * closed no further processors may be started on it.
     * @throws IOException if any processor fails to stop.
     */
    public void close() throws IOException {
        synchronized (this.monitor) {
            if (this.closed) {
                throw new IOException("RpcServer is already closed.");
            }
            this.closed = true;
            IOException lastIOe = null;
            for (RpcProcessor rpcProcessor : this.processors) {
                try {
                    rpcProcessor.stop();
                } catch (IOException ioe) {
                    lastIOe = ioe;
                }
            }
            this.processors.clear();
            if (lastIOe != null) {
                IOException ioe = new IOException("Cannot stop all processors.");
                ioe.initCause(lastIOe);
                throw ioe;
            }
        }
    }

    public Iterator<RpcProcessor> iterator() {
        return this.processors.iterator();
    }

}
