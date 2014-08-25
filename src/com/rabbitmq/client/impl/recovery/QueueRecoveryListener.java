package com.rabbitmq.client.impl.recovery;

/**
 * Not part of the public API. Mean to be used by JVM RabbitMQ clients that build on
 * top of the Java client and need to be notified when server-name queue name changes
 * after recovery.
 */
public interface QueueRecoveryListener {
    void queueRecovered(String oldName, String newName);
}
