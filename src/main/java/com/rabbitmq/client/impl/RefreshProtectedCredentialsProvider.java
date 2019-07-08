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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An abstract {@link CredentialsProvider} that does not let token refresh happen concurrently.
 * <p>
 * A token is usually long-lived (several minutes or more), can be re-used inside the same application,
 * and refreshing it is a costly operation. This base class lets a first call to {@link #refresh()}
 * pass and block concurrent calls until the first call is over. Concurrent calls are then unblocked and
 * can benefit from the refresh. This avoids unnecessary refresh operations to happen if a token
 * is already being renewed.
 * <p>
 * Subclasses need to provide the actual token retrieval (whether is a first retrieval or a renewal is
 * a implementation detail) and how to extract information (username, password, time before expiration)
 * from the retrieved token.
 *
 * @param <T> the type of token (usually specified by the subclass)
 */
public abstract class RefreshProtectedCredentialsProvider<T> implements CredentialsProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshProtectedCredentialsProvider.class);

    private final AtomicReference<T> token = new AtomicReference<>();

    private final Lock refreshLock = new ReentrantLock();
    private final AtomicReference<CountDownLatch> latch = new AtomicReference<>();
    private AtomicBoolean refreshInProcess = new AtomicBoolean(false);

    @Override
    public String getUsername() {
        if (token.get() == null) {
            refresh();
        }
        return usernameFromToken(token.get());
    }

    @Override
    public String getPassword() {
        if (token.get() == null) {
            refresh();
        }
        return passwordFromToken(token.get());
    }

    @Override
    public Duration getTimeBeforeExpiration() {
        if (token.get() == null) {
            refresh();
        }
        return timeBeforeExpiration(token.get());
    }

    @Override
    public void refresh() {
        // refresh should happen at once. Other calls wait for the refresh to finish and move on.
        if (refreshLock.tryLock()) {
            LOGGER.debug("Refreshing token");
            try {
                latch.set(new CountDownLatch(1));
                refreshInProcess.set(true);
                token.set(retrieveToken());
                LOGGER.debug("Token refreshed");
            } finally {
                latch.get().countDown();
                refreshInProcess.set(false);
                refreshLock.unlock();
            }
        } else {
            try {
                LOGGER.debug("Waiting for token refresh to be finished");
                while (!refreshInProcess.get()) {
                    Thread.sleep(10);
                }
                latch.get().await();
                LOGGER.debug("Done waiting for token refresh");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected abstract T retrieveToken();

    protected abstract String usernameFromToken(T token);

    protected abstract String passwordFromToken(T token);

    protected abstract Duration timeBeforeExpiration(T token);
}
