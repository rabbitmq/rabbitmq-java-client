package com.rabbitmq.client.impl.recovery;

public class TopologyRecoveryException extends Exception {
    public TopologyRecoveryException(Throwable cause) {
        super(cause);
    }

    public TopologyRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public TopologyRecoveryException(String message) {
        super(message);
    }
}
