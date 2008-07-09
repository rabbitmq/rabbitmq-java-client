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
