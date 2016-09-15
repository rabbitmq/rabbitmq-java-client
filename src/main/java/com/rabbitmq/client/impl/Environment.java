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

import java.util.concurrent.ThreadFactory;

/**
 * Infers information about the execution environment, e.g.
 * security permissions.
 * Package-protected API.
 */
public class Environment {
    public static boolean isAllowedToModifyThreads() {
        try {
            SecurityManager sm = System.getSecurityManager();
            if(sm != null) {
                sm.checkPermission(new RuntimePermission("modifyThread"));
                sm.checkPermission(new RuntimePermission("modifyThreadGroup"));
            }
            return true;
        } catch (SecurityException se) {
            return false;
        }
    }

    public static Thread newThread(ThreadFactory factory, Runnable runnable, String name) {
        Thread t = factory.newThread(runnable);
        if(isAllowedToModifyThreads()) {
            t.setName(name);
        }
        return t;
    }

    public static Thread newThread(ThreadFactory factory, Runnable runnable, String name, boolean isDaemon) {
        Thread t = newThread(factory, runnable, name);
        if(isAllowedToModifyThreads()) {
            t.setDaemon(isDaemon);
        }
        return t;
    }
}
