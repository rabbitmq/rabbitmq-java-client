package com.rabbitmq.client.impl;

import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.AuthMechanismFactory;

/**
 *
 */
public class ExternalMechanismFactory implements AuthMechanismFactory {
    public AuthMechanism getInstance() {
        return new ExternalMechanism();
    }

    public String getName() {
        return "EXTERNAL";
    }
}
