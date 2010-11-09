package com.rabbitmq.client.impl;

import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.AuthMechanismFactory;

/**
 *
 */
public class PlainMechanismFactory implements AuthMechanismFactory {
    public AuthMechanism getInstance() {
        return new PlainMechanism();
    }

    public String getName() {
        return "PLAIN";
    }
}
