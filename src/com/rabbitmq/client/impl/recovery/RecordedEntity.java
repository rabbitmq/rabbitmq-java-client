package com.rabbitmq.client.impl.recovery;

public class RecordedEntity {
    protected final RecoveringChannel channel;

    public RecordedEntity(RecoveringChannel channel) {
        this.channel = channel;
    }
}
