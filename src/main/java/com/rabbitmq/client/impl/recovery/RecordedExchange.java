package com.rabbitmq.client.impl.recovery;

import java.io.IOException;
import java.util.Map;

/**
 * @since 3.3.0
 */
public class RecordedExchange extends RecordedNamedEntity {
    private boolean durable;
    private boolean autoDelete;
    private Map<String, Object> arguments;
    private String type;

    public RecordedExchange(AutorecoveringChannel channel, String name) {
        super(channel, name);
    }

    public void recover() throws IOException {
        this.channel.getDelegate().exchangeDeclare(this.name, this.type, this.durable, this.autoDelete, this.arguments);
    }

    public RecordedExchange durable(boolean value) {
        this.durable = value;
        return this;
    }

    public RecordedExchange autoDelete(boolean value) {
        this.autoDelete = value;
        return this;
    }

    public RecordedExchange type(String value) {
        this.type = value;
        return this;
    }

    public RecordedExchange arguments(Map<String, Object> value) {
        this.arguments = value;
        return this;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }
}
