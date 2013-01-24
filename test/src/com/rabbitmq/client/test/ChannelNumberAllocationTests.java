//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import junit.framework.TestCase;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class ChannelNumberAllocationTests extends TestCase{
  static int CHANNEL_COUNT = 100;
  static Comparator<Channel> COMPARATOR = new Comparator<Channel>(){
    public int compare(Channel x, Channel y){
      if(x.getChannelNumber() < y.getChannelNumber()) return -1;
      if(x.getChannelNumber() == y.getChannelNumber()) return 0;
      return 1;
    }
  };

  Connection connection;

  public void setUp() throws Exception{
    connection = new ConnectionFactory().newConnection();
  }

  public void tearDown() throws Exception{
    connection.close();
    connection = null;
  }

  public void testAllocateInOrder() throws Exception{
    for(int i = 1; i <= CHANNEL_COUNT; i++)
      assertEquals(i, connection.createChannel().getChannelNumber());
  }

  public void testAllocateAfterFreeingLast() throws Exception{
    Channel ch = connection.createChannel();
    assertEquals(1, ch.getChannelNumber());
    ch.close();
    ch = connection.createChannel();
    assertEquals(1, ch.getChannelNumber());
  }

  public void testAllocateAfterFreeingMany() throws Exception{
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

  public void testAllocateAfterManualAssign() throws Exception{
    connection.createChannel(10);

    for(int i = 0; i < 20; i++)
        assertTrue(10 != connection.createChannel().getChannelNumber());
  }

  public void testManualAllocationDoesntBreakThings() throws Exception{
    connection.createChannel((1 << 16) - 1);
    Channel ch = connection.createChannel();
    assertNotNull(ch);
  }

  public void testReuseManuallyAllocatedChannelNumber1() throws Exception{
    connection.createChannel(1).close();
    assertNotNull(connection.createChannel(1));
  }

  public void testReuseManuallyAllocatedChannelNumber2() throws Exception{
    connection.createChannel(2).close();
    assertNotNull(connection.createChannel(3));
  }

  public void testReserveOnBoundaries() throws Exception{
    assertNotNull(connection.createChannel(3));
    assertNotNull(connection.createChannel(4));
    assertNotNull(connection.createChannel(2));
    assertNotNull(connection.createChannel(5));
    assertNotNull(connection.createChannel(1));
  }
}
