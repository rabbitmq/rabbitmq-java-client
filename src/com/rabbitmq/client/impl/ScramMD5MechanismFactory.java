package com.rabbitmq.client.impl;

import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.AuthMechanismFactory;

/**
 *
 */
public class ScramMD5MechanismFactory implements AuthMechanismFactory {
    public AuthMechanism getInstance() {
        return new ScramMD5Mechanism();
    }

    public String getName() {
        return "RABBIT-SCRAM-MD5";
    }
}
