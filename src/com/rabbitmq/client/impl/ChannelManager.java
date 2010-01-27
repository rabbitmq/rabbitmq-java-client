//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * Manages a set of channels, indexed by channel number.
 */

public class ChannelManager {
    /** Mapping from channel number to AMQChannel instance */
    public final Map<Integer, ChannelN> _channelMap = Collections.synchronizedMap(new HashMap<Integer, ChannelN>());

    public int[] freedChannels = new int[1];
    public int freedChannelsCount = 0;
    public int nextChannelNumber = 1;

    /** Maximum channel number available on this connection. */
    public int _channelMax = 0;

    public synchronized int getChannelMax() {
        return _channelMax;
    }

    public synchronized void setChannelMax(int value) {
        _channelMax = value;
    }

    /**
     * Public API - Looks up an existing channel associated with this connection.
     * @param channelNumber the number of the required channel
     * @return the relevant channel descriptor
     * @throws UnknownChannelException if there is no Channel associated with the 
     *         required channel number.
     */
    public ChannelN getChannel(int channelNumber) {
        ChannelN result = _channelMap.get(channelNumber);
        if(result == null) throw new UnknownChannelException(channelNumber);
        return result;
    }

    public void handleSignal(ShutdownSignalException signal) {
        Set<ChannelN> channels;
        synchronized(_channelMap) {
            channels = new HashSet<ChannelN>(_channelMap.values());
        }
        for (AMQChannel channel : channels) {
            disconnectChannel(channel.getChannelNumber());
            channel.processShutdownSignal(signal, true, true);
        }
    }

    public synchronized ChannelN createChannel(AMQConnection connection) throws IOException {
        int channelNumber = allocateChannelNumber(getChannelMax());
        if (channelNumber == -1) {
            return null;
        }
        return createChannel(connection, channelNumber);
    }

    public synchronized ChannelN createChannel(AMQConnection connection, int channelNumber) throws IOException {
        ChannelN ch = new ChannelN(connection, channelNumber);
        if (_channelMap.containsKey(channelNumber)) {
            // TODO: Returning null here is really dodgy. We should throw a sensible
            // exception. 
            return null; // That number's already allocated! Can't do it
        }
        nextChannelNumber = Math.max(nextChannelNumber, channelNumber + 1);
        addChannel(ch);
        ch.open(); // now that it's been added to our internal tables
        return ch;
    }

    public synchronized int allocateChannelNumber(int maxChannels) {
        if (maxChannels == 0) {
            // The framing encoding only allows for unsigned 16-bit integers for the channel number
            maxChannels = (1 << 16) - 1;
        }
    
        if(freedChannelsCount > 0)
            return freedChannels[--freedChannelsCount];

        if(nextChannelNumber > maxChannels) 
            return -1;

        return nextChannelNumber++;
    }

    public synchronized void freeChannelNumber(int channelNumber){
      if(channelNumber == nextChannelNumber - 1) nextChannelNumber--;
      else {
        if(freedChannelsCount >= freedChannels.length){
          // First we see if there's a chunk of space at the end we can simply 
          // ditch.

          Arrays.sort(freedChannels);

          while(freedChannels[freedChannelsCount - 1] == nextChannelNumber - 1){
            freedChannelsCount--;
            nextChannelNumber--;
          }

          // It's possible this scavenging actually freed up a massive amount of 
          // space. However if it only freed up a little space then we want to 
          // resize the array anyway in order to avoid a case where we're repeatedly
          // sorting the array and only removing a few elements.

          if(freedChannels.length <= 2 * freedChannelsCount + 1){
              int[] newArray = new int[freedChannels.length * 2];
              System.arraycopy(freedChannels, 0, newArray, 0, freedChannels.length);
              freedChannels = newArray;
          }
        }
        freedChannels[freedChannelsCount++] = channelNumber;
      }
    }

    public synchronized void resetChannelAllocation(){
      freedChannelsCount = 0;
      nextChannelNumber = 1;
    }

    private void addChannel(ChannelN chan) {
        _channelMap.put(chan.getChannelNumber(), chan);
    }

    public void disconnectChannel(int channelNumber) {
        _channelMap.remove(channelNumber);
        if(_channelMap.isEmpty()){
          resetChannelAllocation();
        } else {
          freeChannelNumber(channelNumber);
        }
    }
}
