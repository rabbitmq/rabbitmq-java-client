package com.rabbitmq.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface RecoveryDelayHandler {

    long getDelay(final int recoveryAttempts);
    
    public static class DefaultRecoveryDelayHandler implements RecoveryDelayHandler {

        private final long networkRecoveryInterval;
        
        public DefaultRecoveryDelayHandler(final long networkRecoveryInterval) {
            this.networkRecoveryInterval = networkRecoveryInterval;
        }
        
        @Override
        public long getDelay(int recoveryAttempts) {
            return networkRecoveryInterval;
        }
        
    }
    
    public static class ExponentialBackoffDelayHandler implements RecoveryDelayHandler {

        private final List<Long> sequence;
        
        public ExponentialBackoffDelayHandler() {
            sequence = Arrays.asList(0L, 1000L, 1000L, 2000L, 3000L, 5000L, 8000L, 13000L, 21000L);
        }
        
        public ExponentialBackoffDelayHandler(final List<Long> sequence) {
            if (sequence.isEmpty())
                throw new IllegalArgumentException();
            this.sequence = Collections.unmodifiableList(sequence);
            
        }
        
        @Override
        public long getDelay(int recoveryAttempts) {
            return sequence.get(recoveryAttempts >= sequence.size() ? sequence.size() - 1 : recoveryAttempts);
        }
    }
}