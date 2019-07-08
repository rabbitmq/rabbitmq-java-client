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

import org.junit.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class RefreshProtectedCredentialsProviderTest {

    @Test
    public void refresh() throws Exception {
        AtomicInteger retrieveTokenCallCount = new AtomicInteger(0);

        RefreshProtectedCredentialsProvider<TestToken> credentialsProvider = new RefreshProtectedCredentialsProvider<TestToken>() {

            @Override
            protected TestToken retrieveToken() {
                retrieveTokenCallCount.incrementAndGet();
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return new TestToken(UUID.randomUUID().toString());
            }

            @Override
            protected String usernameFromToken(TestToken token) {
                return "";
            }

            @Override
            protected String passwordFromToken(TestToken token) {
                return token.secret;
            }

            @Override
            protected Duration timeBeforeExpiration(TestToken token) {
                return Duration.ofSeconds(1);
            }
        };

        Set<String> passwords = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(5);
        IntStream.range(0, 5).forEach(i -> new Thread(() -> {
            passwords.add(credentialsProvider.getPassword());
            latch.countDown();
        }).start());

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(retrieveTokenCallCount).hasValue(1);
        assertThat(passwords).hasSize(1);
    }

    private static class TestToken {

        final String secret;

        TestToken(String secret) {
            this.secret = secret;
        }
    }

}
