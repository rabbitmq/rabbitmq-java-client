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
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.utility;

import java.util.concurrent.TimeoutException;

/**
 * Simple one-shot IPC mechanism. Essentially a one-place buffer that cannot be emptied once filled.
 */
public class BlockingCell<T> {
    /** Indicator of not-yet-filledness */
    private boolean _filled = false;

    /** Will be null until a value is supplied, and possibly still then. */
    private T _value;
    
    private static final long NANOS_IN_MILLI = 1000 * 1000;
    
    private static final long INFINITY = -1;

    /** Instantiate a new BlockingCell waiting for a value of the specified type. */
    public BlockingCell() {
        // no special handling required in default constructor
    }

    /**
     * Wait for a value, and when one arrives, return it (without clearing it). If there's already a value present, there's no need to wait - the existing value
     * is returned.
     * @return the waited-for value
     *
     * @throws InterruptedException if this thread is interrupted
     */
    public synchronized T get() throws InterruptedException {
        while (!_filled) {
            wait();
        }
        return _value;
    }
    
    /**
     * Wait for a value, and when one arrives, return it (without clearing it). If there's
     * already a value present, there's no need to wait - the existing value is returned.
     * If timeout is reached and value hasn't arrived, TimeoutException is thrown
     * 
     * @param timeout timeout in miliseconds. -1 effectively means infinity
     * @return the waited-for value
     * @throws InterruptedException if this thread is interrupted
     */
    public synchronized T get(long timeout) throws InterruptedException, TimeoutException {
        if (timeout < 0 && timeout != INFINITY)
            throw new AssertionError("Timeout cannot be less than zero");
        
        if (!_filled && timeout != 0) {
            wait(timeout == INFINITY ? 0 : timeout);
        }
        
        if (!_filled)
            throw new TimeoutException();
        
        return _value;
    }
    
    /**
     * As get(), but catches and ignores InterruptedException, retrying until a value appears.
     * @return the waited-for value
     */
    public synchronized T uninterruptibleGet() {
        while (true) {
            try {
                return get();
            } catch (InterruptedException ex) {
                // no special handling necessary
            }
        }
    }
    
    /**
     * As get(long timeout), but catches and ignores InterruptedException, retrying until
     * a value appears or until specified timeout is reached. If timeout is reached,
     * TimeoutException it thrown.
     * We also use System.nanoTime() to behave correctly when system clock jumps around.
     *  
     * @param timeout timeout in miliseconds. -1 effectively means infinity
     * @return the waited-for value
     */
    public synchronized T uninterruptibleGet(int timeout) throws TimeoutException {
        long now = System.nanoTime() / NANOS_IN_MILLI;
        long runTime = now + timeout;
        
        do {
            try {
                return get(runTime - now);
            } catch (InterruptedException e) {
                // Ignore.
            }
        } while ((timeout == INFINITY) || ((now = System.nanoTime() / NANOS_IN_MILLI) < runTime));
        
        throw new TimeoutException();
    }

    /**
     * Store a value in this BlockingCell, throwing AssertionError if the cell already has a value.
     * @param newValue the new value to store
     */
    public synchronized void set(T newValue) {
        if (_filled) {
            throw new AssertionError("BlockingCell can only be set once");
        }
        _value = newValue;
        _filled = true;
        notify();
    }

    /**
     * Store a value in this BlockingCell if it doesn't already have a value.
     * @return true if this call to setIfUnset actually updated the BlockingCell; false if the cell already had a value.
     * @param newValue the new value to store
     */
    public synchronized boolean setIfUnset(T newValue) {
        if (_filled) {
            return false;
        }
        set(newValue);
        _filled = true;
        return true;
    }
}
