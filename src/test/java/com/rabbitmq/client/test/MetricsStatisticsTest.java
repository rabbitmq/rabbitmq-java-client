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
import com.rabbitmq.client.impl.MetricsStatistics;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class MetricsStatisticsTest {

    @Test
    public void basicGetAndAck() {
        MetricsStatistics statistics = new MetricsStatistics();
        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn("connection-1");
        Channel channel = mock(Channel.class);
        when(channel.getConnection()).thenReturn(connection);
        when(channel.getChannelNumber()).thenReturn(1);

        statistics.newConnection(connection);
        statistics.newChannel(channel);

        statistics.consumedMessage(channel, 1, true);
        statistics.consumedMessage(channel, 2, false);
        statistics.consumedMessage(channel, 3, false);
        statistics.consumedMessage(channel, 4, true);
        statistics.consumedMessage(channel, 5, false);
        statistics.consumedMessage(channel, 6, false);

        statistics.basicAck(channel, 6, false);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L));

        statistics.basicAck(channel, 3, true);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+2L));

        statistics.basicAck(channel, 6, true);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));

        statistics.basicAck(channel, 10, true);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));
    }

    @Test public void basicConsumeAndAck() {
        MetricsStatistics statistics = new MetricsStatistics();
        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn("connection-1");
        Channel channel = mock(Channel.class);
        when(channel.getConnection()).thenReturn(connection);
        when(channel.getChannelNumber()).thenReturn(1);

        statistics.newConnection(connection);
        statistics.newChannel(channel);

        String consumerTagWithAutoAck = "1";
        String consumerTagWithManualAck = "2";
        statistics.basicConsume(channel, consumerTagWithAutoAck, true);
        statistics.basicConsume(channel, consumerTagWithManualAck, false);

        statistics.consumedMessage(channel, 1, consumerTagWithAutoAck);
        assertThat(statistics.getConsumedMessages().getCount(), is(1L));
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(0L));

        statistics.consumedMessage(channel, 2, consumerTagWithManualAck);
        statistics.consumedMessage(channel, 3, consumerTagWithManualAck);
        statistics.consumedMessage(channel, 4, consumerTagWithAutoAck);
        statistics.consumedMessage(channel, 5, consumerTagWithManualAck);
        statistics.consumedMessage(channel, 6, consumerTagWithManualAck);

        statistics.basicAck(channel, 6, false);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L));

        statistics.basicAck(channel, 3, true);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+2L));

        statistics.basicAck(channel, 6, true);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));

        statistics.basicAck(channel, 10, true);
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+2L+1L));

    }

    @Test public void cleanStaleState() {
        MetricsStatistics statistics = new MetricsStatistics();
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

        statistics.newConnection(openConnection);
        statistics.newConnection(closedConnection);
        statistics.newChannel(openChannel);
        statistics.newChannel(closedChannel);
        statistics.newChannel(openChannelInClosedConnection);

        assertThat(statistics.getConnections().getCount(), is(2L));
        assertThat(statistics.getChannels().getCount(), is(2L+1L));

        statistics.cleanStaleState();

        assertThat(statistics.getConnections().getCount(), is(1L));
        assertThat(statistics.getChannels().getCount(), is(1L));
    }

}
