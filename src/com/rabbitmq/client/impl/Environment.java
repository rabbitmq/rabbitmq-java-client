package com.rabbitmq.client.impl;

/**
 * Infers information about the execution environment, e.g.
 * security permissions.
 */
class Environment {
    public static boolean isAllowedToModifyThreads() {
        try {
            SecurityManager sm = new SecurityManager();
            sm.checkPermission(new RuntimePermission("modifyThread"));
            sm.checkPermission(new RuntimePermission("modifyThreadGroup"));
            return true;
        } catch (SecurityException se) {
            return false;
        }
    }
}
