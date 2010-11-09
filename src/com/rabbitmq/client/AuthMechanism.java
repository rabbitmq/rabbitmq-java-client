package com.rabbitmq.client;

import com.rabbitmq.client.impl.LongString;

import java.io.IOException;

/**
 * A pluggable authentication mechanism.
 */
public interface AuthMechanism {
    /**
     * Handle one round of challenge-response
     * @param challenge the challenge this round, or null on first round.
     * @param factory for reference to e.g. username and password.
     * @return response
     * @throws IOException
     */
    LongString handleChallenge(LongString challenge, ConnectionFactory factory);
}
