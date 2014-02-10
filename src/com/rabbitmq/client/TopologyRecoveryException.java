package com.rabbitmq.client;

/**
 * Indicates an exception thrown during topology recovery.
 *
 * @see com.rabbitmq.client.ConnectionFactory#setTopologyRecovery(boolean)
 * @since 3.3.0
 */
public class TopologyRecoveryException extends Exception {
    public TopologyRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
