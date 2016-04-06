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
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.impl;

import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * A class that manages {@link ShutdownListener}s and remembers the reason for a shutdown. Both
 * {@link com.rabbitmq.client.Channel Channel}s and {@link com.rabbitmq.client.Connection
 * Connection}s have shutdown listeners. 
 * 
 * The ForwardingShutdownNotifier will only notify listeners when notifyListeners is called,
 *  and may be called multiple times. 
 * 
 * This notifier will always report that it is open.
 */
public class ForwardingShutdownNotifier implements ShutdownNotifier {

    /** Monitor for shutdown listeners and shutdownCause */
    private final Object monitor = new Object();

    /** List of all shutdown listeners associated with the component */
    private final List<ShutdownListener> shutdownListeners = new ArrayList<ShutdownListener>();

    private volatile ShutdownSignalException lastShutdownCause = null;

    public void addShutdownListener(ShutdownListener listener)
    {
        synchronized(this.monitor) {
            this.shutdownListeners.add(listener);
        }
    }

    /**
     * Returns the last close reason notified to listeners.
     */
    public ShutdownSignalException getCloseReason() {
        synchronized(this.monitor) {
            return this.lastShutdownCause;
        }
    }

    /**
     * 
     * Do not call this directly, use notifyListeners(cause)
     */
    public void notifyListeners()
    {
    
    }

    public void removeShutdownListener(ShutdownListener listener)
    {
        synchronized(this.monitor) {
            this.shutdownListeners.remove(listener);
        }
    }

    /** Always reports open */
    public boolean isOpen() {
    	return true;
    }

    /**
     * Notifies the registered listeners of this cause.
     * @param sse
     */
	public void notifyListeners(final ShutdownSignalException sse) {
        ShutdownListener[] sdls = null;
        synchronized(this.monitor) {
            sdls = this.shutdownListeners
                .toArray(new ShutdownListener[this.shutdownListeners.size()]);
            this.lastShutdownCause = sse;
        }
        for (ShutdownListener l: sdls) {
            try {
                l.shutdownCompleted(sse);
            } catch (Exception e) {
            // FIXME: proper logging
            }
        }
 
	}
}
