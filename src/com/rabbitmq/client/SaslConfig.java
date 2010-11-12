package com.rabbitmq.client;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 *
 */
public interface SaslConfig {
    SaslClient getSaslClient(String[] mechanisms) throws SaslException;
}
