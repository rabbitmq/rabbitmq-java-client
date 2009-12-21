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

import com.rabbitmq.client.impl.AMQChannel;

/**
 * This class provides a very stripped-down clone of some of the functionality in
 * java.util.Timer (notably Timer.schedule(TimerTask task, long delay) but 
 * uses System.nanoTime() rather than System.currentTimeMillis() as a measure
 * of the underlying time, and thus behaves correctly if the system clock jumps
 * around.
 * 
 * This class does not have any relation to TimerTask due to the coupling 
 * between TimerTask and Timer - for example if someone invokes 
 * TimerTask.cancel(), we can't find out about it as TimerTask.state is
 * package-private.
 * 
 * We currently just use this to time the quiescing RPC in AMQChannel.
 * 
 * @see AMQChannel
 */

public class SingleShotLinearTimer {
    private volatile Runnable _task;
    private Thread _thread;

    public synchronized void schedule(Runnable task, int timeoutMillisec) {
        if (task == null) {
            throw new IllegalArgumentException("Don't schedule a null task");
        }
        
        if (_task != null) {
            throw new UnsupportedOperationException("Don't schedule more than one task");
        }

        if (timeoutMillisec < 0) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        
        _task = task;
        
        _thread = new Thread(new TimerThread(timeoutMillisec));
        _thread.setDaemon(true);
        _thread.start();
    }
    
    private static final long NANOS_IN_MILLI = 1000 * 1000;
    
    private class TimerThread implements Runnable {
        private long _runTime;
        
        public TimerThread(long timeoutMillisec) {
            _runTime = System.nanoTime() / NANOS_IN_MILLI + timeoutMillisec;
        }

        public void run() {
            try {
                long now;
                while ((now = System.nanoTime() / NANOS_IN_MILLI) < _runTime) {
                    if (_task == null) break;

                    try {
                        synchronized(this) {
                            wait(_runTime - now);
                        }
                    } catch (InterruptedException e) {
                        // Don't care
                    }
                }
                
                Runnable task = _task;
                if (task != null) {
                    task.run();                    
                }

            } finally {
                _task = null;
            }
        }
    }

    public void cancel() {
        _task = null;
    }
}
