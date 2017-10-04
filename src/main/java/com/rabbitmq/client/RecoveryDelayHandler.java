package com.rabbitmq.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A RecoveryDelayHandler is used to tell automatic recovery how long to sleep between reconnect attempts.
 * 
 * @since 4.3.0
 */
public interface RecoveryDelayHandler {

    /**
     * Get the time to sleep (in milliseconds) before attempting to reconnect and recover again.
     * This method will be called with recoveryAttempts=0 before the first recovery attempt and then again after each failed recovery.
     * 
     * @param recoveryAttempts
     *          The number of recovery attempts so far.
     * @return the delay in milliseconds
     */
    long getDelay(final int recoveryAttempts);
    
    /**
     * Basic implementation of {@link RecoveryDelayHandler} that returns the {@link ConnectionFactory#getNetworkRecoveryInterval() network recovery interval} each time.
     */
    public static class DefaultRecoveryDelayHandler implements RecoveryDelayHandler {

        private final long networkRecoveryInterval;
        
        /**
         * Default Constructor
         * @param networkRecoveryInterval
         *          recovery delay time in millis
         */
        public DefaultRecoveryDelayHandler(final long networkRecoveryInterval) {
            this.networkRecoveryInterval = networkRecoveryInterval;
        }
        
        @Override
        public long getDelay(int recoveryAttempts) {
            return networkRecoveryInterval;
        }
    }
    
    /**
     * Backoff implementation of {@link RecoveryDelayHandler} that uses the Fibonacci sequence (by default) to increase the recovery delay time after each failed attempt.
     * You can optionally use your own backoff sequence.
     */
    public static class ExponentialBackoffDelayHandler implements RecoveryDelayHandler {

        private final List<Long> sequence;
        
        /**
         * Default Constructor. Uses the fibonacci sequence: {0, 1000, 1000, 2000, 3000, 5000, 8000, 13000, 21000}.
         */
        public ExponentialBackoffDelayHandler() {
            sequence = Arrays.asList(0L, 1000L, 1000L, 2000L, 3000L, 5000L, 8000L, 13000L, 21000L);
        }
        
        /**
         * Constructor for passing your own backoff sequence
         * 
         * @param sequence
         *          List of recovery delay values in milliseconds.
         * @throws IllegalArgumentException if the sequence is null or empty
         */
        public ExponentialBackoffDelayHandler(final List<Long> sequence) {
            if (sequence == null || sequence.isEmpty())
                throw new IllegalArgumentException();
            this.sequence = Collections.unmodifiableList(sequence);
        }
        
        @Override
        public long getDelay(int recoveryAttempts) {
            return sequence.get(recoveryAttempts >= sequence.size() ? sequence.size() - 1 : recoveryAttempts);
        }
    }
}