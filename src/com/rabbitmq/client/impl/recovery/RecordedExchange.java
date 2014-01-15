package com.rabbitmq.client.impl.recovery;

import java.io.*;
import java.util.*;

public class RecordedExchange extends RecordedNamedEntity implements RecoverableEntity {
    public static final String EMPTY_STRING = "";
    private static final String DEFAULT_EXCHANGE_NAME = "";
    private boolean durable;
    private boolean autoDelete;
    private Map<String, Object> arguments;
    private boolean exclusive;
    private boolean serverNamed;
    private String type;

    public RecordedExchange(AutorecoveringChannel channel, String name) {
        super(channel, name);
    }

    public Object recover() throws IOException {
        return this.channel.exchangeDeclare(this.name, this.type, this.durable, this.autoDelete, this.arguments);
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

    public static boolean isPredefined(String exchange) {
        return exchange.equals(DEFAULT_EXCHANGE_NAME) || exchange.toLowerCase().startsWith("amq.");

    }
}
