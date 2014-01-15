package com.rabbitmq.client.impl.recovery;

public class RecordedNamedEntity extends RecordedEntity {
    protected String name;

    public RecordedNamedEntity(AutorecoveringChannel channel, String name) {
        super(channel);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
