package com.rabbitmq.client.impl.recovery;

/**
 * @since 3.3.0
 */
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
