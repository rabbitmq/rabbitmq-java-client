package com.rabbitmq.client.test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.impl.ConcurrentStatistics;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 *
 */
public class ConcurrentStatisticsTest {

    @Test
    public void basicGetAndAck() {
        ConcurrentStatistics statistics = new ConcurrentStatistics();
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
        assertEquals(1, statistics.getAcknowledgedMessageCount());

        statistics.basicAck(channel, 3, true);
        assertEquals(1+2, statistics.getAcknowledgedMessageCount());

        statistics.basicAck(channel, 6, true);
        assertEquals(1+2+1, statistics.getAcknowledgedMessageCount());

        statistics.basicAck(channel, 10, true);
        assertEquals(1+2+1, statistics.getAcknowledgedMessageCount());
    }

    @Test public void basicConsumeAndAck() {
        ConcurrentStatistics statistics = new ConcurrentStatistics();
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
        assertEquals(1, statistics.getConsumedMessageCount());
        assertEquals(0, statistics.getAcknowledgedMessageCount());

        statistics.consumedMessage(channel, 2, consumerTagWithManualAck);
        statistics.consumedMessage(channel, 3, consumerTagWithManualAck);
        statistics.consumedMessage(channel, 4, consumerTagWithAutoAck);
        statistics.consumedMessage(channel, 5, consumerTagWithManualAck);
        statistics.consumedMessage(channel, 6, consumerTagWithManualAck);

        statistics.basicAck(channel, 6, false);
        assertEquals(1, statistics.getAcknowledgedMessageCount());

        statistics.basicAck(channel, 3, true);
        assertEquals(1+2, statistics.getAcknowledgedMessageCount());

        statistics.basicAck(channel, 6, true);
        assertEquals(1+2+1, statistics.getAcknowledgedMessageCount());

        statistics.basicAck(channel, 10, true);
        assertEquals(1+2+1, statistics.getAcknowledgedMessageCount());

    }

}
