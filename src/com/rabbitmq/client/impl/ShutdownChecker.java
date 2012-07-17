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
//  Copyright (c) 2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to check connections at JVM shutdown.
 * <p>
 * <code>ShutdownChecker</code> holds a set of {@link AMQConnection}s and registers a shutdown hook to
 * check when the JVM performs an orderly shutdown.
 * </p>
 * <p>
 * This class offers two public methods to add a connection and to remove a connection, and one public method to set the
 * checker -- which registers the hook (and returns itself, so the call can be chained).
 * </p>
 * <p>
 * <strong>Concurrent Semantics</strong><br />
 * This class is fully thread-safe.
 * </p>
 */
class ShutdownChecker {

    private final Set<AMQConnection>
        checkedConnections = Collections.synchronizedSet(new HashSet<AMQConnection>());

    public final ShutdownChecker set() {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                ShutdownChecker.this.checkConnections();
            }
        });
        return this;
    }

    public final void addShutdownCheck(AMQConnection connection) {
        this.checkedConnections.add(connection);
    }

    public final void removeShutdownCheck(AMQConnection connection) {
        this.checkedConnections.remove(connection);
    }

    private final void checkConnections() {
        synchronized (this.checkedConnections) {
            for (AMQConnection c: this.checkedConnections) {
                checkIsNotOpen(c);
            }
        }
    }

    /**
     * Issues warning if the connection object is open.
     */
    private final void checkIsNotOpen(AMQConnection connection) {
        if (connection.isOpen())
            System.err.println("WARNING: Connection ("
                             + connection.toString()
                             + ") was open during JVM shutdown. "
                             + "Messages may be lost."
                             );
    }
}
