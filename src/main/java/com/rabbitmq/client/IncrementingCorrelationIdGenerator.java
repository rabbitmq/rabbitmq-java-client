package com.rabbitmq.client;

import java.util.function.Supplier;

public class IncrementingCorrelationIdGenerator implements Supplier<String> {

    private final String _prefix;
    private int _correlationId;

    public IncrementingCorrelationIdGenerator(String _prefix) {
        this._prefix = _prefix;
    }

    @Override
    public String get() {
        return _prefix + _correlationId++;
    }
}
