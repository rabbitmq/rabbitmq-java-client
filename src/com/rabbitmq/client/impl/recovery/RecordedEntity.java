package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Channel;

/**
 * @since 3.3.0
 */
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
