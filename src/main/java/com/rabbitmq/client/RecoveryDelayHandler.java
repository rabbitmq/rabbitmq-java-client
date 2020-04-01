// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

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
    class DefaultRecoveryDelayHandler implements RecoveryDelayHandler {

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
    class ExponentialBackoffDelayHandler implements RecoveryDelayHandler {

        private final List<Long> sequence;
        
        /**
         * Default Constructor. Uses the following sequence: 2000, 3000, 5000, 8000, 13000, 21000, 34000
         */
        public ExponentialBackoffDelayHandler() {
            sequence = Arrays.asList(2000L, 3000L, 5000L, 8000L, 13000L, 21000L, 34000L);
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
            int index = recoveryAttempts >= sequence.size() ? sequence.size() - 1 : recoveryAttempts;
            return sequence.get(index);
        }
    }
}
