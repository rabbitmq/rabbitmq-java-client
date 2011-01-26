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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.impl;

import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownNotifier;
import com.rabbitmq.client.ShutdownSignalException;

public class ShutdownNotifierComponent implements ShutdownNotifier {

    /** Monitor for listeners and shutdownCause */
    private final Object monitor = new Object();

    /** List of all shutdown listeners associated with the component */
    private final List<ShutdownListener> shutdownListeners
        = new ArrayList<ShutdownListener>();

    /**
     * When this value is null, the component is in an "open"
     * state. When non-null, the component is in "closed" state, and
     * this value indicates the circumstances of the shutdown.
     */
    private volatile ShutdownSignalException shutdownCause = null;

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

    public ShutdownSignalException getCloseReason() {
        return this.shutdownCause;
    }

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

    public void removeShutdownListener(ShutdownListener listener)
    {
        synchronized(this.monitor) {
            this.shutdownListeners.remove(listener);
        }
    }

    public boolean isOpen() {
        return this.shutdownCause == null;
    }

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
