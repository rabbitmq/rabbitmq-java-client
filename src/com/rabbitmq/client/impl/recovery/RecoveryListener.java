package com.rabbitmq.client.impl.recovery;

interface RecoveryListener {
    public void handleRecovery(Recoverable recoverable);
}
