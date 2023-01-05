// Copyright (c) 2018-2023 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.stubbing.OngoingStubbing;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class ConnectionTest {

    @Mock
    MyConnection c = mock(MyConnection.class);
    @Mock
    Channel ch = mock(Channel.class);

    AutoCloseable mocks;

    public static Object[] configurators() {
        return new Object[]{new NotNumberedChannelCreationCallback(), new NumberedChannelCreationCallback()};
    }

    @BeforeEach
    public void init() {
        mocks = openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("configurators")
    public void openChannelWithNonNullChannelShouldReturnNonEmptyOptional(TestConfigurator configurator) throws Exception {
        configurator.mockAndWhenChannel(c).thenReturn(ch);
        configurator.mockAndWhenOptional(c).thenCallRealMethod();
        Optional<Channel> optional = configurator.open(c);
        assertTrue(optional.isPresent());
        assertSame(ch, optional.get());
    }

    @ParameterizedTest
    @MethodSource("configurators")
    public void openChannelWithNullChannelShouldReturnEmptyOptional(TestConfigurator configurator) throws Exception {
        configurator.mockAndWhenChannel(c).thenReturn(null);
        configurator.mockAndWhenOptional(c).thenCallRealMethod();
        Assertions.assertThatThrownBy(() -> {
            Optional<Channel> optional = configurator.open(c);
            assertFalse(optional.isPresent());
            optional.get();
        }).isInstanceOf(NoSuchElementException.class);
    }

    @ParameterizedTest
    @MethodSource("configurators")
    public void openChannelShouldPropagateIoException(TestConfigurator configurator) throws Exception {
        configurator.mockAndWhenChannel(c).thenThrow(IOException.class);
        configurator.mockAndWhenOptional(c).thenCallRealMethod();
        Assertions.assertThatThrownBy(() -> configurator.open(c)).isInstanceOf(IOException.class);
    }

    interface TestConfigurator {

        OngoingStubbing<Channel> mockAndWhenChannel(Connection c) throws IOException;

        OngoingStubbing<Optional<Channel>> mockAndWhenOptional(Connection c) throws IOException;

        Optional<Channel> open(Connection c) throws IOException;

    }

    static class NotNumberedChannelCreationCallback implements TestConfigurator {

        @Override
        public OngoingStubbing<Channel> mockAndWhenChannel(Connection c) throws IOException {
            return when(c.createChannel());
        }

        @Override
        public OngoingStubbing<Optional<Channel>> mockAndWhenOptional(Connection c) throws IOException {
            return when(c.openChannel());
        }

        @Override
        public Optional<Channel> open(Connection c) throws IOException {
            return c.openChannel();
        }
    }

    static class NumberedChannelCreationCallback implements TestConfigurator {

        @Override
        public OngoingStubbing<Channel> mockAndWhenChannel(Connection c) throws IOException {
            return when(c.createChannel(1));
        }

        @Override
        public OngoingStubbing<Optional<Channel>> mockAndWhenOptional(Connection c) throws IOException {
            return when(c.openChannel(1));
        }

        @Override
        public Optional<Channel> open(Connection c) throws IOException {
            return c.openChannel(1);
        }
    }

    // trick to make Mockito call the optional method defined in the interface
    static abstract class MyConnection implements Connection {

    }

}
