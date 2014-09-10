package com.rabbitmq.client;

/**
 * Provides a way to register (network, AMQP 0-9-1) connection recovery
 * callbacks.
 */
public interface Recoverable {
    /**
     * Registers a connection recovery callback.
     *
     * @param f Callback function
     */
    public void addRecoveryListener(RecoveryListener listener);

    public void removeRecoveryListener(RecoveryListener listener);
}
