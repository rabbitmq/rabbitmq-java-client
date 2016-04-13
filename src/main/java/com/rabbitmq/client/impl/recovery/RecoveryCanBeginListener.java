package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * Used internally to indicate when connection recovery can
 * begin. See {@link https://github.com/rabbitmq/rabbitmq-java-client/issues/135}.
 * This is package-local by design.
 */
public interface RecoveryCanBeginListener {
    void recoveryCanBegin(ShutdownSignalException cause);
}
