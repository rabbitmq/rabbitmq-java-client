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
 */
public class ShutdownNotifierComponent implements ShutdownNotifier {

    /** Monitor for shutdown listeners and shutdownCause */
    private final Object monitor = new Object();

    /** List of all shutdown listeners associated with the component */
    private final List<ShutdownListener> shutdownListeners = new ArrayList<ShutdownListener>();

    /**
     * When this value is null, the component is in an "open"
     * state. When non-null, the component is in "closed" state, and
     * this value indicates the circumstances of the shutdown.
     */
    private volatile ShutdownSignalException shutdownCause = null;

    @Override
    public void addShutdownListener(ShutdownListener listener)
    {
        ShutdownSignalException sse = null;
        synchronized(this.monitor) {
            sse = this.shutdownCause;
            this.shutdownListeners.add(listener);
        }
        if (sse != null) // closed
            listener.shutdownCompleted(sse);
    }

    @Override
    public ShutdownSignalException getCloseReason() {
        synchronized(this.monitor) {
            return this.shutdownCause;
        }
    }

    @Override
    public void notifyListeners()
    {
        ShutdownSignalException sse = null;
        ShutdownListener[] sdls = null;
        synchronized(this.monitor) {
            sdls = this.shutdownListeners
                .toArray(new ShutdownListener[this.shutdownListeners.size()]);
            sse = this.shutdownCause;
        }
        for (ShutdownListener l: sdls) {
            try {
                l.shutdownCompleted(sse);
            } catch (Exception e) {
            // FIXME: proper logging
            }
        }
    }

    @Override
    public void removeShutdownListener(ShutdownListener listener)
    {
        synchronized(this.monitor) {
            this.shutdownListeners.remove(listener);
        }
    }

    @Override
    public boolean isOpen() {
        synchronized(this.monitor) {
            return this.shutdownCause == null;
        }
    }

    /**
     * Internal: this is the means of registering shutdown.
     * @param sse the reason for the shutdown
     * @return <code>true</code> if the component is open; <code>false</code> otherwise.
     */
    public boolean setShutdownCauseIfOpen(ShutdownSignalException sse) {
        synchronized (this.monitor) {
            if (isOpen()) {
                this.shutdownCause = sse;
                return true;
            }
            return false;
        }
    }
}
