package com.rabbitmq.client.impl;

import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.ConnectionFactory;

/**
 * The EXTERNAL auth mechanism
 */
public class ExternalMechanism implements AuthMechanism {
    public LongString handleChallenge(LongString challenge, ConnectionFactory factory) {
        return LongStringHelper.asLongString("");
    }
}
