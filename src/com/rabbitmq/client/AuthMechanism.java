package com.rabbitmq.client;

import com.rabbitmq.client.impl.LongString;

import java.io.IOException;

/**
 * A pluggable authentication mechanism. Should be stateless.
 */
public interface AuthMechanism {
    /**
     * Handle one round of challenge-response
     * @param round zero-based counter of the current round
     * @param challenge the challenge this round, or null on round 0.
     * @param factory for reference to e.g. username and password.
     * @return response
     * @throws IOException
     */
    LongString handleChallenge(int round, LongString challenge, ConnectionFactory factory);

    /**
     * The name of the authentication mechanism, as negotiated on the wire
     */
    String getName();
}
