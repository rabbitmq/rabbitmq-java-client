// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.stubbing.OngoingStubbing;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(Parameterized.class)
public class ConnectionTest {

    @Parameterized.Parameter
    public TestConfigurator configurator;
    @Mock
    MyConnection c = mock(MyConnection.class);
    @Mock
    Channel ch = mock(Channel.class);

    @Parameterized.Parameters
    public static Object[] configurators() {
        return new Object[]{new NotNumberedChannelCreationCallback(), new NumberedChannelCreationCallback()};
    }

    @Before
    public void init() {
        initMocks(this);
    }

    @Test
    public void openChannelWithNonNullChannelShouldReturnNonEmptyOptional() throws Exception {
        configurator.mockAndWhenChannel(c).thenReturn(ch);
        configurator.mockAndWhenOptional(c).thenCallRealMethod();
        Optional<Channel> optional = configurator.open(c);
        assertTrue(optional.isPresent());
        assertSame(ch, optional.get());
    }

    @Test(expected = NoSuchElementException.class)
    public void openChannelWithNullChannelShouldReturnEmptyOptional() throws Exception {
        configurator.mockAndWhenChannel(c).thenReturn(null);
        configurator.mockAndWhenOptional(c).thenCallRealMethod();
        Optional<Channel> optional = configurator.open(c);
        assertFalse(optional.isPresent());
        optional.get();
    }

    @Test(expected = IOException.class)
    public void openChannelShouldPropagateIoException() throws Exception {
        configurator.mockAndWhenChannel(c).thenThrow(IOException.class);
        configurator.mockAndWhenOptional(c).thenCallRealMethod();
        configurator.open(c);
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
