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
import com.rabbitmq.client.ConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

public class ChannelNumberAllocationTests {
  static final int CHANNEL_COUNT = 100;
  static final Comparator<Channel> COMPARATOR = new Comparator<Channel>(){
    public int compare(Channel x, Channel y){
      if(x.getChannelNumber() < y.getChannelNumber()) return -1;
      if(x.getChannelNumber() == y.getChannelNumber()) return 0;
      return 1;
    }
  };

  Connection connection;

  @Before public void setUp() throws Exception{
    connection = TestUtils.connectionFactory().newConnection();
  }

  @After public void tearDown() throws Exception{
    connection.close();
    connection = null;
  }

  @Test public void allocateInOrder() throws Exception{
    for(int i = 1; i <= CHANNEL_COUNT; i++)
      assertEquals(i, connection.createChannel().getChannelNumber());
  }

  @Test public void allocateAfterFreeingLast() throws Exception{
    Channel ch = connection.createChannel();
    assertEquals(1, ch.getChannelNumber());
    ch.close();
    ch = connection.createChannel();
    assertEquals(1, ch.getChannelNumber());
  }

  @Test public void allocateAfterFreeingMany() throws Exception{
    List<Channel> channels = new ArrayList<Channel>();

    for(int i = 1; i <= CHANNEL_COUNT; i++)
      channels.add(connection.createChannel());

    for(Channel channel : channels){
      channel.close();
    }

    channels = new ArrayList<Channel>();

    for(int i = 1; i <= CHANNEL_COUNT; i++)
      channels.add(connection.createChannel());

    // In the current implementation the allocated numbers need not be increasing
    Collections.sort(channels, COMPARATOR);

    assertEquals("Didn't create the right number of channels!", CHANNEL_COUNT, channels.size());
    for(int i = 1; i < CHANNEL_COUNT; ++i) {
      assertTrue("Channel numbers should be distinct."
          , channels.get(i-1).getChannelNumber() < channels.get(i).getChannelNumber()
      );
    }
  }

  @Test public void allocateAfterManualAssign() throws Exception{
    connection.createChannel(10);

    for(int i = 0; i < 20; i++)
      assertTrue(10 != connection.createChannel().getChannelNumber());
  }

  @Test public void manualAllocationDoesntBreakThings() throws Exception{
    connection.createChannel((1 << 16) - 1);
    Channel ch = connection.createChannel();
    assertNotNull(ch);
  }

  @Test public void reuseManuallyAllocatedChannelNumber1() throws Exception{
    connection.createChannel(1).close();
    assertNotNull(connection.createChannel(1));
  }

  @Test public void reuseManuallyAllocatedChannelNumber2() throws Exception{
    connection.createChannel(2).close();
    assertNotNull(connection.createChannel(3));
  }

  @Test public void reserveOnBoundaries() throws Exception{
    assertNotNull(connection.createChannel(3));
    assertNotNull(connection.createChannel(4));
    assertNotNull(connection.createChannel(2));
    assertNotNull(connection.createChannel(5));
    assertNotNull(connection.createChannel(1));
  }

  @Test public void channelMaxIs2047() {
    int channelMaxServerSide = 2048;
    int defaultChannelCount = 1;
    assertEquals(channelMaxServerSide - defaultChannelCount, connection.getChannelMax());
  }
}
