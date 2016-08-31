// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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
import com.rabbitmq.client.impl.StandardMetricsCollector;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class StandardMetricsCollectorTest {

    @Test
    public void basicGetAndAck() {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn("connection-1");
        Channel channel = mock(Channel.class);
        when(channel.getConnection()).thenReturn(connection);
        when(channel.getChannelNumber()).thenReturn(1);

        metrics.newConnection(connection);
        metrics.newChannel(channel);

        metrics.consumedMessage(channel, 1, true);
        metrics.consumedMessage(channel, 2, false);
        metrics.consumedMessage(channel, 3, false);
        metrics.consumedMessage(channel, 4, true);
        metrics.consumedMessage(channel, 5, false);
        metrics.consumedMessage(channel, 6, false);

        metrics.basicAck(channel, 6, false);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L));

        metrics.basicAck(channel, 3, true);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L+2L));

        metrics.basicAck(channel, 6, true);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));

        metrics.basicAck(channel, 10, true);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));
    }

    @Test public void basicConsumeAndAck() {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn("connection-1");
        Channel channel = mock(Channel.class);
        when(channel.getConnection()).thenReturn(connection);
        when(channel.getChannelNumber()).thenReturn(1);

        metrics.newConnection(connection);
        metrics.newChannel(channel);

        String consumerTagWithAutoAck = "1";
        String consumerTagWithManualAck = "2";
        metrics.basicConsume(channel, consumerTagWithAutoAck, true);
        metrics.basicConsume(channel, consumerTagWithManualAck, false);

        metrics.consumedMessage(channel, 1, consumerTagWithAutoAck);
        assertThat(metrics.getConsumedMessages().getCount(), is(1L));
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(0L));

        metrics.consumedMessage(channel, 2, consumerTagWithManualAck);
        metrics.consumedMessage(channel, 3, consumerTagWithManualAck);
        metrics.consumedMessage(channel, 4, consumerTagWithAutoAck);
        metrics.consumedMessage(channel, 5, consumerTagWithManualAck);
        metrics.consumedMessage(channel, 6, consumerTagWithManualAck);

        metrics.basicAck(channel, 6, false);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L));

        metrics.basicAck(channel, 3, true);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L+2L));

        metrics.basicAck(channel, 6, true);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));

        metrics.basicAck(channel, 10, true);
        assertThat(metrics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));

    }

    @Test public void cleanStaleState() {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        Connection openConnection = mock(Connection.class);
        when(openConnection.getId()).thenReturn("connection-1");
        when(openConnection.isOpen()).thenReturn(true);

        Channel openChannel = mock(Channel.class);
        when(openChannel.getConnection()).thenReturn(openConnection);
        when(openChannel.getChannelNumber()).thenReturn(1);
        when(openChannel.isOpen()).thenReturn(true);

        Channel closedChannel = mock(Channel.class);
        when(closedChannel.getConnection()).thenReturn(openConnection);
        when(closedChannel.getChannelNumber()).thenReturn(2);
        when(closedChannel.isOpen()).thenReturn(false);

        Connection closedConnection = mock(Connection.class);
        when(closedConnection.getId()).thenReturn("connection-2");
        when(closedConnection.isOpen()).thenReturn(false);

        Channel openChannelInClosedConnection = mock(Channel.class);
        when(openChannelInClosedConnection.getConnection()).thenReturn(closedConnection);
        when(openChannelInClosedConnection.getChannelNumber()).thenReturn(1);
        when(openChannelInClosedConnection.isOpen()).thenReturn(true);

        metrics.newConnection(openConnection);
        metrics.newConnection(closedConnection);
        metrics.newChannel(openChannel);
        metrics.newChannel(closedChannel);
        metrics.newChannel(openChannelInClosedConnection);

        assertThat(metrics.getConnections().getCount(), is(2L));
        assertThat(metrics.getChannels().getCount(), is(2L+1L));

        metrics.cleanStaleState();

        assertThat(metrics.getConnections().getCount(), is(1L));
        assertThat(metrics.getChannels().getCount(), is(1L));
    }

}
