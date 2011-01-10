package com.rabbitmq.client;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * This interface represents a hook to allow you to control how exactly
 * a sasl client is selected during authentication.
 * @see com.rabbitmq.client.ConnectionFactory
 */
public interface SaslConfig {
    SaslClient getSaslClient(String[] mechanisms) throws SaslException;
}
