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

import com.rabbitmq.client.AMQP;

/**
 * Class to close connections at JVM termination.
 * <p>
 * <code>ShutdownChecker</code> holds a set of {@link AMQConnection}s and registers a shutdown hook to
 * be executed when the JVM performs an orderly termination.
 * </p>
 * <p>
 * This class offers two public methods to add and to remove a connection, and one public method to set the
 * checker -- which registers the hook (and returns itself, so the call can be chained).
 * </p>
 * <p>
 * <strong>Concurrent Semantics</strong><br />
 * This class is fully thread-safe.
 * </p>
 */
class ShutdownChecker {

    private static final int TERMINATION_TIMEOUT = 1000; // ms
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
                checkAndCloseIfNotOpen(c);
            }
        }
    }

    /**
     * If the connection is open, it is closed. An error message is issued if this fails.
     */
    private final void checkAndCloseIfNotOpen(AMQConnection connection) {
        if (connection.isOpen()) {
            String connStr = connection.toString();
            try {
                connection.close(AMQP.CONNECTION_FORCED,
                    "Connection closed during JVM termination.",
                    TERMINATION_TIMEOUT);
                System.err.println("WARNING: Connection (" + connStr
                    + ") was closed during JVM termination.");
            } catch (Exception e) {
                System.err.println("ERROR: Connection (" + connStr
                    + ") threw exception " + e
                    + " while being closed during JVM termination. "
                    + "Messages may have been lost.");
            }
        }
    }
}
