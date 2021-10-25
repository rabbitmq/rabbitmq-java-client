// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import java.util.concurrent.ThreadFactory;

/**
 * Infers information about the execution environment, e.g.
 * security permissions.
 * Package-protected API.
 */
public class Environment {

    /**
     * This method is deprecated and subject to removal in the next major release.
     *
     * There is no replacement for this method, as it used to use the
     * {@link SecurityManager}, which is itself deprecated and subject to removal.
     * @deprecated
     * @return always returns true
     */
    @Deprecated
    public static boolean isAllowedToModifyThreads() {
       return true;
    }

    public static Thread newThread(ThreadFactory factory, Runnable runnable, String name) {
        Thread t = factory.newThread(runnable);
        t.setName(name);
        return t;
    }

    public static Thread newThread(ThreadFactory factory, Runnable runnable, String name, boolean isDaemon) {
        Thread t = newThread(factory, runnable, name);
        t.setDaemon(isDaemon);
        return t;
    }
}
