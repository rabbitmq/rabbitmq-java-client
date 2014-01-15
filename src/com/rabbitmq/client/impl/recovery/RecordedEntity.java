package com.rabbitmq.client.impl.recovery;

public class RecordedEntity {
    protected final AutorecoveringChannel channel;

    public RecordedEntity(AutorecoveringChannel channel) {
        this.channel = channel;
    }
}
