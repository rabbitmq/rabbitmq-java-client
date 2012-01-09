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
 * of them so that they may all be stopped when the server is closed.
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

    public static RpcServer newServer(Channel channel) {
        return new RpcServer(channel);
    }

    public void startProcessor(RpcProcessor processor) throws IOException {
        synchronized (this.monitor) {
            if (this.closed) {
                throw new IOException("RpcServer is already closed.");
            }
            if (processors.contains(processor))
                throw new IOException("RpcProcessor already in RpcServer.");
            processors.add(processor);
            processor.start(this.channel);
        }
    }

    public void close() throws IOException {
        synchronized (this.monitor) {
            if (this.closed) {
                throw new IOException("RpcServer is already closed.");
            }
            this.closed = true;
            IOException lastIOe = null;
            for (RpcProcessor rpcProcessor : processors) {
                try {
                    rpcProcessor.stop();
                } catch (IOException ioe) {
                    lastIOe = ioe;
                }
            }
            processors.clear();
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
