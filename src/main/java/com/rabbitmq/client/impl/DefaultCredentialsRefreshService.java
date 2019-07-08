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
 * a <code>Function<Duration, Long> refreshDelayStrategy</code>. Once credentials
 * for a {@link CredentialsProvider} have been renewed, the callback registered
 * by each entity/connection is performed. This callback typically propagates
 * the new credentials in the entity state, e.g. sending the new password to the
 * broker for AMQP connections.
 * <p>
 * Instances are preferably created with {@link DefaultCredentialsRefreshServiceBuilder}.
 */
public class DefaultCredentialsRefreshService implements CredentialsRefreshService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCredentialsRefreshService.class);

    /**
     * Scheduler used to schedule credentials refresh.
     * <p>
     * Default is a single-threaded scheduler, which should be enough for most scenarios, assuming
     * that credentials expire after a few minutes or hours. This default scheduler
     * is automatically disposed of when the {@link DefaultCredentialsRefreshService} is closed.
     * <p>
     * If an external scheduler is passed in, it is the developer's responsibility to
     * close it.
     */
    private final ScheduledExecutorService scheduler;

    private final ConcurrentMap<CredentialsProvider, CredentialsProviderState> credentialsProviderStates = new ConcurrentHashMap<>();

    private final boolean privateScheduler;

    /**
     * Strategy to schedule credentials refresh after credentials retrieval.
     * <p>
     * Typical strategies schedule refresh after a ratio of the time before expiration
     * (e.g. 80 % of the time before expiration) or after a fixed time before
     * expiration (e.g. 20 seconds before credentials expire).
     *
     * @see #ratioRefreshDelayStrategy(double)
     * @see #fixedDelayBeforeExpirationRefreshDelayStrategy(Duration)
     */
    private final Function<Duration, Duration> refreshDelayStrategy;

    /**
     * Strategy to provide a hint about whether credentials should be renewed now or not before attempting to connect.
     * <p>
     * This can avoid a connection to use almost expired credentials if this connection
     * is created just before credentials are refreshed in the background, but does not
     * benefit from the refresh.
     * <p>
     * Note setting such a strategy may require knowledge of the credentials validity and must be consistent
     * with the {@link #refreshDelayStrategy} chosen. For example, for a validity of 60 minutes and
     * a {@link #refreshDelayStrategy} that instructs to refresh 10 minutes before credentials expire, this
     * strategy could hint that credentials that expire in 11 minutes or less (1 minute before a refresh is actually
     * scheduled) should be refreshed, which would trigger an early refresh.
     * <p>
     * The default strategy always return false.
     */
    private final Function<Duration, Boolean> approachingExpirationStrategy;

    /**
     * Constructor. Consider using {@link DefaultCredentialsRefreshServiceBuilder} to create instances.
     *
     * @param scheduler
     * @param refreshDelayStrategy
     * @param approachingExpirationStrategy
     */
    public DefaultCredentialsRefreshService(ScheduledExecutorService scheduler, Function<Duration, Duration> refreshDelayStrategy, Function<Duration, Boolean> approachingExpirationStrategy) {
        if (refreshDelayStrategy == null) {
            throw new IllegalArgumentException("Refresh delay strategy can not be null");
        }
        this.refreshDelayStrategy = refreshDelayStrategy;
        this.approachingExpirationStrategy = approachingExpirationStrategy == null ? duration -> false : approachingExpirationStrategy;
        if (scheduler == null) {
            this.scheduler = Executors.newScheduledThreadPool(1);
            privateScheduler = true;
        } else {
            this.scheduler = scheduler;
            privateScheduler = false;
        }
    }

    /**
     * Delay before refresh is a ratio of the time before expiration.
     * <p>
     * E.g. if time before expiration is 60 minutes and specified ratio is 0.8, refresh will
     * be scheduled in 60 x 0.8 = 48 minutes.
     *
     * @param ratio
     * @return the delay before refreshing
     */
    public static Function<Duration, Duration> ratioRefreshDelayStrategy(double ratio) {
        return new RatioRefreshDelayStrategy(ratio);
    }

    /**
     * Delay before refresh is <code>time before expiration - specified duration</code>.
     * <p>
     * E.g. if time before expiration is 60 minutes and specified duration is 10 minutes, refresh will
     * be scheduled in 60 - 10 = 50 minutes.
     *
     * @param duration
     * @return the delay before refreshing
     */
    public static Function<Duration, Duration> fixedDelayBeforeExpirationRefreshDelayStrategy(Duration duration) {
        return new FixedDelayBeforeExpirationRefreshDelayStrategy(duration);
    }

    /**
     * Advise to refresh credentials if <code>TTL <= limit</code>.
     *
     * @param limitBeforeExpiration
     * @return true if credentials should be refreshed, false otherwise
     */
    public static Function<Duration, Boolean> fixedTimeApproachingExpirationStrategy(Duration limitBeforeExpiration) {
        return new FixedTimeApproachingExpirationStrategy(limitBeforeExpiration.toMillis());
    }

    private static Runnable refresh(ScheduledExecutorService scheduler, CredentialsProviderState credentialsProviderState,
                                    Function<Duration, Duration> refreshDelayStrategy) {
        return () -> {
            LOGGER.debug("Refreshing token");
            credentialsProviderState.refresh();

            Duration timeBeforeExpiration = credentialsProviderState.credentialsProvider.getTimeBeforeExpiration();
            Duration newDelay = refreshDelayStrategy.apply(timeBeforeExpiration);

            LOGGER.debug("Scheduling refresh in {} seconds", newDelay.getSeconds());

            ScheduledFuture<?> scheduledFuture = scheduler.schedule(
                    refresh(scheduler, credentialsProviderState, refreshDelayStrategy),
                    newDelay.getSeconds(),
                    TimeUnit.SECONDS
            );
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
            Duration delay = refreshDelayStrategy.apply(credentialsProvider.getTimeBeforeExpiration());
            LOGGER.debug("Scheduling refresh in {} seconds", delay.getSeconds());
            return scheduler.schedule(
                    refresh(scheduler, credentialsProviderState, refreshDelayStrategy),
                    delay.getSeconds(),
                    TimeUnit.SECONDS
            );
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
    public boolean isApproachingExpiration(Duration timeBeforeExpiration) {
        return this.approachingExpirationStrategy.apply(timeBeforeExpiration);
    }

    public void close() {
        if (privateScheduler) {
            scheduler.shutdownNow();
        }
    }

    private static class FixedTimeApproachingExpirationStrategy implements Function<Duration, Boolean> {

        private final long limitBeforeExpiration;

        private FixedTimeApproachingExpirationStrategy(long limitBeforeExpiration) {
            this.limitBeforeExpiration = limitBeforeExpiration;
        }

        @Override
        public Boolean apply(Duration timeBeforeExpiration) {
            return timeBeforeExpiration.toMillis() <= limitBeforeExpiration;
        }
    }

    private static class FixedDelayBeforeExpirationRefreshDelayStrategy implements Function<Duration, Duration> {

        private final Duration delay;

        private FixedDelayBeforeExpirationRefreshDelayStrategy(Duration delay) {
            this.delay = delay;
        }

        @Override
        public Duration apply(Duration timeBeforeExpiration) {
            Duration refreshTimeBeforeExpiration = timeBeforeExpiration.minus(delay);
            if (refreshTimeBeforeExpiration.isNegative()) {
                return timeBeforeExpiration;
            } else {
                return refreshTimeBeforeExpiration;
            }
        }
    }

    private static class RatioRefreshDelayStrategy implements Function<Duration, Duration> {

        private final double ratio;

        private RatioRefreshDelayStrategy(double ratio) {
            if (ratio < 0 || ratio > 1) {
                throw new IllegalArgumentException("Ratio should be > 0 and <= 1: " + ratio);
            }
            this.ratio = ratio;
        }

        @Override
        public Duration apply(Duration duration) {
            return Duration.ofSeconds((long) ((double) duration.getSeconds() * ratio));
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
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            int attemptCount = 0;
            boolean refreshSucceeded = false;
            while (attemptCount < 3) {
                LOGGER.debug("Refreshing token for credentials provider {}", credentialsProvider);
                try {
                    this.credentialsProvider.refresh();
                    LOGGER.debug("Token refreshed for credentials provider {}", credentialsProvider);
                    refreshSucceeded = true;
                    break;
                } catch (Exception e) {
                    LOGGER.warn("Error while trying to refresh token: {}", e.getMessage());
                }
                attemptCount++;
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (!refreshSucceeded) {
                LOGGER.warn("Token refresh failed after retry, aborting callbacks");
                return;
            }

            Iterator<Registration> iterator = registrations.values().iterator();
            while (iterator.hasNext() && !Thread.currentThread().isInterrupted()) {
                Registration registration = iterator.next();
                // FIXME set a timeout on the call? (needs a separate thread)
                try {
                    boolean refreshed = registration.refreshAction.call();
                    if (!refreshed) {
                        LOGGER.debug("Registration did not refresh token");
                        iterator.remove();
                    }
                    registration.errorHistory.set(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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

    /**
     * Builder to create instances of {@link DefaultCredentialsRefreshServiceBuilder}.
     */
    public static class DefaultCredentialsRefreshServiceBuilder {


        private ScheduledExecutorService scheduler;

        private Function<Duration, Duration> refreshDelayStrategy = ratioRefreshDelayStrategy(0.8);

        private Function<Duration, Boolean> approachingExpirationStrategy = ttl -> false;

        public DefaultCredentialsRefreshServiceBuilder scheduler(ScheduledThreadPoolExecutor scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Set the strategy to schedule credentials refresh after credentials retrieval.
         * <p>
         * Default is a 80 % ratio-based strategy (refresh is scheduled after 80 % of the time
         * before expiration, e.g. 48 minutes for a token with a validity of 60 minutes, that
         * is refresh will be scheduled 12 minutes before the token actually expires).
         *
         * @param refreshDelayStrategy
         * @return this builder instance
         * @see DefaultCredentialsRefreshService#refreshDelayStrategy
         * @see DefaultCredentialsRefreshService#ratioRefreshDelayStrategy(double)
         */
        public DefaultCredentialsRefreshServiceBuilder refreshDelayStrategy(Function<Duration, Duration> refreshDelayStrategy) {
            this.refreshDelayStrategy = refreshDelayStrategy;
            return this;
        }

        /**
         * Set the strategy to trigger an early refresh before attempting to connect.
         * <p>
         * Default is to never advise to refresh before connecting.
         *
         * @param approachingExpirationStrategy
         * @return this builder instances
         * @see DefaultCredentialsRefreshService#approachingExpirationStrategy
         * @see CredentialsRefreshService#isApproachingExpiration(Duration)
         */
        public DefaultCredentialsRefreshServiceBuilder approachingExpirationStrategy(Function<Duration, Boolean> approachingExpirationStrategy) {
            this.approachingExpirationStrategy = approachingExpirationStrategy;
            return this;
        }

        /**
         * Create the {@link DefaultCredentialsRefreshService} instance.
         *
         * @return
         */
        public DefaultCredentialsRefreshService build() {
            return new DefaultCredentialsRefreshService(scheduler, refreshDelayStrategy, approachingExpirationStrategy);
        }

    }

}
