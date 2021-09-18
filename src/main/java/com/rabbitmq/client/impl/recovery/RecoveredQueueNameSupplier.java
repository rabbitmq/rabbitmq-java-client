package com.rabbitmq.client.impl.recovery;

/**
 * Functional callback interface that can be used to rename a queue during topology recovery.
 * Can use along with {@link QueueRecoveryListener} to know when such a queue has been recovered successfully.
 * 
 * @see QueueRecoveryListener
 */
@FunctionalInterface
public interface RecoveredQueueNameSupplier {

    /**
     * Get the queue name to use when recovering this RecordedQueue entity
     * 
     * 
     * @param recordedQueue the queue to be recovered
     * @return new queue name
     */
    String getNameToUseForRecovery(final RecordedQueue recordedQueue);
}