package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.*;

import java.io.*;
import java.util.*;

public class RecordedQueue extends RecordedNamedEntity implements RecoverableEntity {
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

    public Object recover() throws IOException {
        AMQP.Queue.DeclareOk ok = this.channel.queueDeclare(this.getNameToUseForRecovery(), this.durable, this.exclusive, this.autoDelete, this.arguments);
        this.name = ok.getQueue();

        return ok;
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
