package com.rabbitmq.client;

/**
 * A RecoveryListener receives notifications about completed automatic connection
 * recovery.
 *
 * @since 3.3.0
 */
public interface RecoveryListener {
    public void handleRecovery(Recoverable recoverable);
}
