package com.rabbitmq.client.impl;

import java.util.concurrent.ThreadFactory;

/**
 * Infers information about the execution environment, e.g.
 * security permissions.
 */
class Environment {
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
