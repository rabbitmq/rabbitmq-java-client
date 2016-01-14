package com.rabbitmq.client.impl.recovery;

/**
 * Not part of the public API. Mean to be used by JVM RabbitMQ clients that build on
 * top of the Java client and need to be notified when consumer tag changes
 * after recovery.
 */
public interface ConsumerRecoveryListener {
    void consumerRecovered(String oldConsumerTag, String newConsumerTag);
}
