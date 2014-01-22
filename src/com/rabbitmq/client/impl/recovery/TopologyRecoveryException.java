package com.rabbitmq.client.impl.recovery;

public class TopologyRecoveryException extends Exception {
    public TopologyRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
