package com.rabbitmq.client;

/**
 * Enum for built-in exchange types.
 */
public enum BuiltinExchangeType {

    DIRECT("direct"), FANOUT("fanout"), TOPIC("topic"), HEADERS("headers"),
    CONSISTENT_HASH("x-consistent-hash"), MODULUS_HASH("x-modulus-hash"),
    LOCAL_RANDOM("x-local-random"), RANDOM("x-random");

    private final String type;

    BuiltinExchangeType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
