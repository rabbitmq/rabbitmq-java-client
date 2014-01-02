package com.rabbitmq.client.impl.recovery;

public interface RecoveryListener {
    public void handleRecovery(Recoverable recoverable);
}
