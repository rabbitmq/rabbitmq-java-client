package com.rabbitmq.client;

/**
 * Provides a way to register (network, AMQP 0-9-1) connection recovery
 * callbacks.
 *
 * When connection recovery is enabled via {@link ConnectionFactory},
 * {@link ConnectionFactory#newConnection()} and {@link Connection#createChannel()}
 * return {@link Recoverable} connections and channels.
 *
 * @see com.rabbitmq.client.impl.recovery.AutorecoveringConnection
 * @see com.rabbitmq.client.impl.recovery.AutorecoveringChannel
 */
public interface Recoverable {
    /**
     * Registers a connection recovery callback.
     *
     * @param listener Callback function
     */
    public void addRecoveryListener(RecoveryListener listener);

    public void removeRecoveryListener(RecoveryListener listener);
}
