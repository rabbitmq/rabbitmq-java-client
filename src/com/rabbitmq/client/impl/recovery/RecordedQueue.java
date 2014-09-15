package com.rabbitmq.client.impl.recovery;

import java.io.IOException;
import java.util.Map;

/**
 * @since 3.3.0
 */
public class RecordedQueue extends RecordedNamedEntity {
    public static final String EMPTY_STRING = "";
    private boolean durable;
    private boolean autoDelete;
    private Map<String, Object> arguments;
    private boolean exclusive;
    private boolean serverNamed;

    public RecordedQueue(AutorecoveringChannel channel, String name) {
        super(channel, name);
    }

    public RecordedQueue exclusive(boolean value) {
        this.exclusive = value;
        return this;
    }

    public RecordedQueue serverNamed(boolean value) {
        this.serverNamed = value;
        return this;
    }

    public boolean isServerNamed() {
        return this.serverNamed;
    }

    public boolean isAutoDelete() { return this.autoDelete; }

    public void recover() throws IOException {
        this.name = this.channel.queueDeclare(this.getNameToUseForRecovery(),
                                                     this.durable,
                                                     this.exclusive,
                                                     this.autoDelete,
                                                     this.arguments).getQueue();
    }

    public String getNameToUseForRecovery() {
        if(isServerNamed()) {
            return EMPTY_STRING;
        } else {
            return this.name;
        }
    }

    public RecordedQueue durable(boolean value) {
        this.durable = value;
        return this;
    }

    public RecordedQueue autoDelete(boolean value) {
        this.autoDelete = value;
        return this;
    }

    public RecordedQueue arguments(Map<String, Object> value) {
        this.arguments = value;
        return this;
    }
}
