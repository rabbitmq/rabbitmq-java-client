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

package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.impl.Environment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
public abstract class AbstractNioLoop implements Runnable {

    final NioParams nioParams;

    protected AbstractNioLoop(NioParams nioParams) {
        this.nioParams = nioParams;
    }

    protected void handleIoError(SocketChannelFrameHandlerState state, Throwable ex) {
        if(needToDispatchIoError(state)) {
            dispatchIoErrorToConnection(state, ex);
        }
    }

    protected boolean needToDispatchIoError(final SocketChannelFrameHandlerState state) {
        return state.getConnection().isOpen();
    }

    protected void dispatchIoErrorToConnection(final SocketChannelFrameHandlerState state, final Throwable ex) {
        // In case of recovery after the shutdown,
        // the new connection shouldn't be initialized in
        // the NIO thread, to avoid a deadlock.
        Runnable shutdown = new Runnable() {
            @Override
            public void run() {
                state.getConnection().handleIoError(ex);
            }
        };
        if(executorService() == null) {
            String name = "rabbitmq-connection-shutdown-" + state.getConnection();
            Thread shutdownThread = Environment.newThread(threadFactory(), shutdown, name);
            shutdownThread.start();
        } else {
            executorService().submit(shutdown);
        }
    }

    protected void dispatchShutdownToConnection(final SocketChannelFrameHandlerState state) {
        Runnable shutdown = new Runnable() {
            @Override
            public void run() {
                state.getConnection().doFinalShutdown();
            }
        };
        if(executorService() == null) {
            String name = "rabbitmq-connection-shutdown-" + state.getConnection();
            Thread shutdownThread = Environment.newThread(threadFactory(), shutdown, name);
            shutdownThread.start();
        } else {
            executorService().submit(shutdown);
        }
    }

    private ExecutorService executorService() {
        return nioParams.getNioExecutor();
    }

    private ThreadFactory threadFactory() {
        return nioParams.getThreadFactory();
    }

}
