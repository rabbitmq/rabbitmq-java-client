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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.impl;

import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
import com.rabbitmq.client.ShutdownSignalException;

public class ShutdownNotifierComponent implements ShutdownNotifier {

    /** List of all shutdown listeners associated with the component */
    public List<ShutdownListener> listeners
            = new ArrayList<ShutdownListener>();

    /**
     * When this value is null, the component is in an "open"
     * state. When non-null, the component is in "closed" state, and
     * this value indicates the circumstances of the shutdown.
     */
    public volatile ShutdownSignalException _shutdownCause = null;

    public void addShutdownListener(ShutdownListener listener)
    {
        boolean closed = false;
        synchronized(listeners) {
            closed = !isOpen();
            listeners.add(listener);
        }
        if (closed)
            listener.shutdownCompleted(getCloseReason());
    }

    public ShutdownSignalException getCloseReason() {
        return _shutdownCause;
    }

    public void notifyListeners()
    {
        synchronized(listeners) {
            for (ShutdownListener l: listeners)
                try {
                    l.shutdownCompleted(getCloseReason());
                } catch (Exception e) {
                    // FIXME: proper logging
                }
        }
    }

    public void removeShutdownListener(ShutdownListener listener)
    {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }

    public boolean isOpen() {
        return _shutdownCause == null;
    }

}
