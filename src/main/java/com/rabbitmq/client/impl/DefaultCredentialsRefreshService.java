// Copyright (c) 2019 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Scheduling-based implementation of {@link CredentialsRefreshService}.
 * <p>
 * This implementation keeps track of entities (typically AMQP connections) that need
 * to renew credentials. Token renewal is scheduled based on token expiration, using
 * a <code>Function<Date, Long> refreshDelayStrategy</code>. Once credentials
 * for a {@link CredentialsProvider} have been renewed, the callback registered
 * by each entity/connection is performed. This callback typically propagates
 * the new credentials in the entity state, e.g. sending the new password to the
 * broker for AMQP connections.
 */
public class DefaultCredentialsRefreshService implements CredentialsRefreshService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCredentialsRefreshService.class);

    private final ScheduledExecutorService scheduler;

    private final ConcurrentMap<CredentialsProvider, CredentialsProviderState> credentialsProviderStates = new ConcurrentHashMap<>();

    private final boolean privateScheduler;

    private final Function<Date, Long> refreshDelayStrategy;

    private final Function<Date, Boolean> needRefreshStrategy;

    public DefaultCredentialsRefreshService(ScheduledExecutorService scheduler, Function<Date, Long> refreshDelayStrategy, Function<Date, Boolean> needRefreshStrategy) {
        this.refreshDelayStrategy = refreshDelayStrategy;
        this.needRefreshStrategy = needRefreshStrategy;
        if (scheduler == null) {
            this.scheduler = Executors.newScheduledThreadPool(1);
            privateScheduler = true;
        } else {
            this.scheduler = scheduler;
            privateScheduler = false;
        }
    }

    /**
     * Delay before refresh is <code>TTL - specified duration</code>.
     * <p>
     * E.g. if TTL is 60 seconds and specified duration is 20 seconds, refresh will
     * be scheduled in 60 - 20 = 40 seconds.
     *
     * @param duration
     * @return
     */
    public static Function<Date, Long> fixedDelayBeforeExpirationRefreshDelayStrategy(Duration duration) {
        return new FixedDelayBeforeExpirationRefreshDelayStrategy(duration.toMillis());
    }

    /**
     * Advise to refresh credentials if <code>TTL <= limit</code>.
     *
     * @param limitBeforeExpiration
     * @return
     */
    public static Function<Date, Boolean> fixedTimeNeedRefreshStrategy(Duration limitBeforeExpiration) {
        return new FixedTimeNeedRefreshStrategy(limitBeforeExpiration.toMillis());
    }

    // TODO add a delay refresh strategy that bases the time on a percentage of the TTL, use it as default with 80% TTL

    private static Runnable refresh(ScheduledExecutorService scheduler, CredentialsProviderState credentialsProviderState,
                                    Function<Date, Long> refreshDelayStrategy) {
        return () -> {
            LOGGER.debug("Refreshing token");
            credentialsProviderState.refresh();

            Date expirationAfterRefresh = credentialsProviderState.credentialsProvider.getExpiration();
            long newDelay = refreshDelayStrategy.apply(expirationAfterRefresh);

            LOGGER.debug("Scheduling refresh in {} milliseconds", newDelay);

            ScheduledFuture<?> scheduledFuture = scheduler.schedule(refresh(scheduler, credentialsProviderState, refreshDelayStrategy), newDelay, TimeUnit.MILLISECONDS);
            credentialsProviderState.refreshTask.set(scheduledFuture);
        };
    }

    @Override
    public String register(CredentialsProvider credentialsProvider, Callable<Boolean> refreshAction) {
        String registrationId = UUID.randomUUID().toString();
        LOGGER.debug("New registration {}", registrationId);

        Registration registration = new Registration(registrationId, refreshAction);
        CredentialsProviderState credentialsProviderState = credentialsProviderStates.computeIfAbsent(
                credentialsProvider,
                credentialsProviderKey -> new CredentialsProviderState(credentialsProviderKey)
        );

        credentialsProviderState.add(registration);

        credentialsProviderState.maybeSetRefreshTask(() -> {
            Date expiration = credentialsProvider.getExpiration();
            long delay = refreshDelayStrategy.apply(expiration);
            LOGGER.debug("Scheduling refresh in {} milliseconds", delay);
            return scheduler.schedule(refresh(scheduler, credentialsProviderState, refreshDelayStrategy), delay, TimeUnit.MILLISECONDS);
        });

        return registrationId;
    }

    @Override
    public void unregister(CredentialsProvider credentialsProvider, String registrationId) {
        CredentialsProviderState credentialsProviderState = this.credentialsProviderStates.get(credentialsProvider);
        if (credentialsProviderState != null) {
            credentialsProviderState.unregister(registrationId);
        }
    }

    @Override
    public boolean needRefresh(Date expiration) {
        return this.needRefreshStrategy.apply(expiration);
    }

    public void close() {
        if (privateScheduler) {
            scheduler.shutdownNow();
        }
    }

    private static class FixedTimeNeedRefreshStrategy implements Function<Date, Boolean> {

        private final long limitBeforeExpiration;

        private FixedTimeNeedRefreshStrategy(long limitBeforeExpiration) {
            this.limitBeforeExpiration = limitBeforeExpiration;
        }

        @Override
        public Boolean apply(Date expiration) {
            long ttl = expiration.getTime() - new Date().getTime();
            return ttl <= limitBeforeExpiration;
        }
    }

    private static class FixedDelayBeforeExpirationRefreshDelayStrategy implements Function<Date, Long> {

        private final long delay;

        private FixedDelayBeforeExpirationRefreshDelayStrategy(long delay) {
            this.delay = delay;
        }

        @Override
        public Long apply(Date expiration) {
            long ttl = expiration.getTime() - new Date().getTime();
            long refreshTimeBeforeExpiration = ttl - delay;
            if (refreshTimeBeforeExpiration < 0) {
                return ttl;
            } else {
                return refreshTimeBeforeExpiration;
            }
        }
    }

    static class Registration {

        private final Callable<Boolean> refreshAction;

        private final AtomicInteger errorHistory = new AtomicInteger(0);

        private final String id;

        Registration(String id, Callable<Boolean> refreshAction) {
            this.refreshAction = refreshAction;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Registration that = (Registration) o;

            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    /**
     * State and refresh behavior for a {@link CredentialsProvider} and
     * its registered entities.
     */
    static class CredentialsProviderState {

        private final CredentialsProvider credentialsProvider;

        private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

        private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();

        private final AtomicBoolean refreshTaskSet = new AtomicBoolean(false);

        CredentialsProviderState(CredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
        }

        void add(Registration registration) {
            this.registrations.put(registration.id, registration);
        }

        void maybeSetRefreshTask(Supplier<ScheduledFuture<?>> scheduledFutureSupplier) {
            if (refreshTaskSet.compareAndSet(false, true)) {
                refreshTask.set(scheduledFutureSupplier.get());
            }
        }

        void refresh() {
            // FIXME check whether thread has been cancelled or not before refresh() and registratAction.call()

            // FIXME protect this call, or at least log some error
            this.credentialsProvider.refresh();

            Iterator<Registration> iterator = registrations.values().iterator();
            while (iterator.hasNext()) {
                Registration registration = iterator.next();
                // FIXME set a timeout on the call? (needs a separate thread)
                try {
                    boolean refreshed = registration.refreshAction.call();
                    if (!refreshed) {
                        LOGGER.debug("Registration did not refresh token");
                        iterator.remove();
                    }
                    registration.errorHistory.set(0);
                } catch (Exception e) {
                    LOGGER.warn("Error while trying to refresh a connection token", e);
                    registration.errorHistory.incrementAndGet();
                    if (registration.errorHistory.get() >= 5) {
                        registrations.remove(registration.id);
                    }
                }
            }
        }

        void unregister(String registrationId) {
            this.registrations.remove(registrationId);
        }
    }

    public static class DefaultCredentialsRefreshServiceBuilder {


        private ScheduledExecutorService scheduler;

        private Function<Date, Long> refreshDelayStrategy = fixedDelayBeforeExpirationRefreshDelayStrategy(Duration.ofSeconds(60));

        private Function<Date, Boolean> needRefreshStrategy = fixedTimeNeedRefreshStrategy(Duration.ofSeconds(60));

        public DefaultCredentialsRefreshServiceBuilder scheduler(ScheduledThreadPoolExecutor scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public DefaultCredentialsRefreshServiceBuilder refreshDelayStrategy(Function<Date, Long> refreshDelayStrategy) {
            this.refreshDelayStrategy = refreshDelayStrategy;
            return this;
        }

        public DefaultCredentialsRefreshServiceBuilder needRefreshStrategy(Function<Date, Boolean> needRefreshStrategy) {
            this.needRefreshStrategy = needRefreshStrategy;
            return this;
        }

        public DefaultCredentialsRefreshService build() {
            return new DefaultCredentialsRefreshService(scheduler, refreshDelayStrategy, needRefreshStrategy);
        }

    }

}
