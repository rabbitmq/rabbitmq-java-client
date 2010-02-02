package com.rabbitmq.client.test;

import com.rabbitmq.client.*;
import junit.framework.TestCase;
import java.util.*;

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
    connection = new ConnectionFactory().newConnection("localhost");
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

    // In the current implementation the list should actually
    // already be sorted, but we don't want to force that behaviour
    Collections.sort(channels, COMPARATOR);

    int i = 1;
    for(Channel channel : channels)
      assertEquals(i++, channel.getChannelNumber());
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
}
