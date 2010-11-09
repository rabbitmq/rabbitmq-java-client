package com.rabbitmq.client.impl;

import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.ConnectionFactory;

/**
 * The PLAIN auth mechanism
 */
public class PlainMechanism implements AuthMechanism {
    public LongString handleChallenge(int round, LongString challenge,
                                      ConnectionFactory factory) {
        return LongStringHelper.asLongString("\0" + factory.getUsername() +
                                             "\0" + factory.getPassword());
    }

    public String getName() {
        return "PLAIN";
    }
}
