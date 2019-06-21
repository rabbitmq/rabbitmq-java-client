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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DefaultCredentialsRefreshServiceTest {

    @Mock
    Callable<Boolean> refreshAction;

    @Mock
    CredentialsProvider credentialsProvider;

    DefaultCredentialsRefreshService refreshService;

    @After
    public void tearDown() {
        if (refreshService != null) {
            refreshService.close();
        }
    }

    @Test
    public void scheduling() throws Exception {
        refreshService = new DefaultCredentialsRefreshService.DefaultCredentialsRefreshServiceBuilder()
                .refreshDelayStrategy(DefaultCredentialsRefreshService.fixedDelayBeforeExpirationRefreshDelayStrategy(Duration.ofSeconds(2)))
                .build();

        AtomicInteger passwordSequence = new AtomicInteger(0);
        when(credentialsProvider.getPassword()).thenAnswer(
                (Answer<String>) invocation -> "password-" + passwordSequence.get());
        when(credentialsProvider.getExpiration()).thenAnswer((Answer<Date>) invocation -> {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, 5);
            return calendar.getTime();
        });
        doAnswer(invocation -> {
            passwordSequence.incrementAndGet();
            return null;
        }).when(credentialsProvider).refresh();

        List<String> passwords = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2 * 2);
        refreshAction = () -> {
            passwords.add(credentialsProvider.getPassword());
            latch.countDown();
            return true;
        };
        refreshService.register(credentialsProvider, refreshAction);
        refreshService.register(credentialsProvider, refreshAction);
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(passwords).hasSize(4).containsExactlyInAnyOrder("password-1", "password-2", "password-1", "password-2");

        AtomicInteger passwordSequence2 = new AtomicInteger(0);
        CredentialsProvider credentialsProvider2 = mock(CredentialsProvider.class);
        when(credentialsProvider2.getPassword()).thenAnswer((Answer<String>) invocation -> "password2-" + passwordSequence2.get());
        when(credentialsProvider2.getExpiration()).thenAnswer((Answer<Date>) invocation -> {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, 4);
            return calendar.getTime();
        });
        doAnswer(invocation -> {
            passwordSequence2.incrementAndGet();
            return null;
        }).when(credentialsProvider2).refresh();


        List<String> passwords2 = new CopyOnWriteArrayList<>();
        CountDownLatch latch2 = new CountDownLatch(2 * 1);
        refreshAction = () -> {
            passwords2.add(credentialsProvider2.getPassword());
            latch2.countDown();
            return true;
        };

        refreshService.register(credentialsProvider2, refreshAction);

        assertThat(latch2.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(passwords2).hasSize(2).containsExactlyInAnyOrder(
                "password2-1", "password2-2"
        );
        assertThat(passwords).hasSizeGreaterThan(4);


    }

    @Test
    public void refreshActionIsCorrectlyRegisteredCalledAndCanceled() throws Exception {
        DefaultCredentialsRefreshService.CredentialsProviderState state = new DefaultCredentialsRefreshService.CredentialsProviderState(
                credentialsProvider
        );
        when(refreshAction.call()).thenReturn(true);
        state.add(new DefaultCredentialsRefreshService.Registration("1", refreshAction));

        state.refresh();
        verify(credentialsProvider, times(1)).refresh();
        verify(refreshAction, times(1)).call();

        state.refresh();
        verify(credentialsProvider, times(2)).refresh();
        verify(refreshAction, times(2)).call();

        state.unregister("1");
        state.refresh();
        verify(credentialsProvider, times(3)).refresh();
        verify(refreshAction, times(2)).call();
    }

    @Test
    public void refreshActionIsRemovedIfItReturnsFalse() throws Exception {
        DefaultCredentialsRefreshService.CredentialsProviderState state = new DefaultCredentialsRefreshService.CredentialsProviderState(
                credentialsProvider
        );
        when(refreshAction.call()).thenReturn(false);
        state.add(new DefaultCredentialsRefreshService.Registration("1", refreshAction));

        state.refresh();
        verify(credentialsProvider, times(1)).refresh();
        verify(refreshAction, times(1)).call();

        state.refresh();
        verify(credentialsProvider, times(2)).refresh();
        verify(refreshAction, times(1)).call();
    }

    @Test
    public void refreshActionIsRemovedIfItErrorsTooMuch() throws Exception {
        DefaultCredentialsRefreshService.CredentialsProviderState state = new DefaultCredentialsRefreshService.CredentialsProviderState(
                credentialsProvider
        );
        when(refreshAction.call()).thenThrow(RuntimeException.class);
        state.add(new DefaultCredentialsRefreshService.Registration("1", refreshAction));

        int callsCountBeforeCancellation = 5;
        IntStream.range(0, callsCountBeforeCancellation).forEach(i -> {
            state.refresh();
        });

        verify(credentialsProvider, times(callsCountBeforeCancellation)).refresh();
        verify(refreshAction, times(callsCountBeforeCancellation)).call();

        state.refresh();
        verify(credentialsProvider, times(callsCountBeforeCancellation + 1)).refresh();
        verify(refreshAction, times(callsCountBeforeCancellation)).call();
    }

}
