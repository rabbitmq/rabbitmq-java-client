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

package com.rabbitmq.client.test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.DefaultCredentialsRefreshService;
import com.rabbitmq.client.impl.RefreshProtectedCredentialsProvider;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RefreshCredentialsTest {

    @ClassRule
    public static TestRule brokerVersionTestRule = TestUtils.atLeast38();
    DefaultCredentialsRefreshService refreshService;

    @Before
    public void tearDown() {
        if (refreshService != null) {
            refreshService.close();
        }
    }

    @Test
    public void connectionAndRefreshCredentials() throws Exception {
        ConnectionFactory cf = TestUtils.connectionFactory();
        CountDownLatch latch = new CountDownLatch(5);
        RefreshProtectedCredentialsProvider<TestToken> provider = new RefreshProtectedCredentialsProvider<TestToken>() {
            @Override
            protected TestToken retrieveToken() {
                latch.countDown();
                return new TestToken("guest", 2, Instant.now());
            }

            @Override
            protected String usernameFromToken(TestToken token) {
                return "guest";
            }

            @Override
            protected String passwordFromToken(TestToken token) {
                return token.secret;
            }

            @Override
            protected Duration timeBeforeExpiration(TestToken token) {
                return token.getTimeBeforeExpiration();
            }
        };

        cf.setCredentialsProvider(provider);
        refreshService = new DefaultCredentialsRefreshService.DefaultCredentialsRefreshServiceBuilder()
                .refreshDelayStrategy(DefaultCredentialsRefreshService.fixedDelayBeforeExpirationRefreshDelayStrategy(Duration.ofSeconds(1)))
                .approachingExpirationStrategy(expiration -> false)
                .build();
        cf.setCredentialsRefreshService(refreshService);

        try (Connection c = cf.newConnection()) {
            Channel ch = c.createChannel();
            String queue = ch.queueDeclare().getQueue();
            TestUtils.sendAndConsumeMessage("", queue, queue, c);
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static class TestToken {

        final String secret;
        final int expiresIn;
        final Instant receivedAt;

        TestToken(String secret, int expiresIn, Instant receivedAt) {
            this.secret = secret;
            this.expiresIn = expiresIn;
            this.receivedAt = receivedAt;
        }

        public Duration getTimeBeforeExpiration() {
            Instant now = Instant.now();
            long age = receivedAt.until(now, ChronoUnit.SECONDS);
            return Duration.ofSeconds(expiresIn - age);
        }
    }

}
