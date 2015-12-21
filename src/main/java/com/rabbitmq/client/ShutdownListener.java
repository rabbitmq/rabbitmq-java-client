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


package com.rabbitmq.client;

import java.util.EventListener;

/**
 * A ShutdownListener receives information about the shutdown of connections and
 * channels. Note that when a connection is shut down, its associated channels are also
 * considered shut down and their ShutdownListeners will be notified (with the same cause).
 * Because of this, and the fact that channel ShutdownListeners execute in the connection's
 * thread, attempting to make blocking calls on a connection inside the listener will
 * lead to deadlock.
 *
 * @see ShutdownNotifier
 * @see ShutdownSignalException
 */
public interface ShutdownListener extends EventListener {
    public void shutdownCompleted(ShutdownSignalException cause);
}
