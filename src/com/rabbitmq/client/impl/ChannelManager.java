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

import com.rabbitmq.client.ShutdownSignalException;

/**
 * Manages a set of channels, indexed by channel number.
 */

public class ChannelManager {
    /** Mapping from channel number to AMQChannel instance */
    private final Map<Integer, ChannelN> _channelMap = Collections.synchronizedMap(new HashMap<Integer, ChannelN>());

    /** Maximum number of channels available on this connection. */
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
     */
    public ChannelN getChannel(int channelNumber) {
        return _channelMap.get(channelNumber);
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
            return null; // That number's already allocated! Can't do it
        }
        addChannel(ch);
        ch.open(); // now that it's been added to our internal tables
        return ch;
    }

    public synchronized int allocateChannelNumber(int maxChannels) {
        if (maxChannels == 0) {
            maxChannels = Integer.MAX_VALUE;
        }
        int channelNumber = -1;
        for (int candidate = 1; candidate < maxChannels; candidate++) {
            if (!_channelMap.containsKey(candidate)) {
                channelNumber = candidate;
                break;
            }
        }
        return channelNumber;
    }

    private void addChannel(ChannelN chan) {
        _channelMap.put(chan.getChannelNumber(), chan);
    }

    public void disconnectChannel(int channelNumber) {
        _channelMap.remove(channelNumber);
    }
}
