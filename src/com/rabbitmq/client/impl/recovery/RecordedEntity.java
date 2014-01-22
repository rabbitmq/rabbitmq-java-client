package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Channel;

public class RecordedEntity {
    protected final AutorecoveringChannel channel;

    public RecordedEntity(AutorecoveringChannel channel) {
        this.channel = channel;
    }

    public AutorecoveringChannel getChannel() {
        return channel;
    }

    public Channel getDelegateChannel() {
        return channel.getDelegate();
    }
}
