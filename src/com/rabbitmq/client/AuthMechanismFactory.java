package com.rabbitmq.client;

/**
 *
 */
public interface AuthMechanismFactory {
    /**
     * @return a new authentication mechanism implementation
     */
    public AuthMechanism getInstance();

    /**
     * The name of the authentication mechanism, as negotiated on the wire
     */
    public String getName();
}
