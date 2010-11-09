package com.rabbitmq.client;

import com.rabbitmq.client.impl.AMQChannel;

import java.io.IOException;

/**
 * A pluggable authentication mechanism
 */
public interface AuthMechanism {
    /**
     * Send and receive start-ok / secure / secure-ok until a connection is
     * established or an exception thrown.
     * 
     * @param channel to send methods on
     * @param factory for reference to e.g. username and password.
     * @return the Connection.Tune method sent by the server after authentication
     * @throws IOException if the authentication failed or something else went wrong
     */
    AMQP.Connection.Tune doLogin(AMQChannel channel, ConnectionFactory factory) throws IOException;

    /**
     * The name of the authentication mechanism, as negotiated on the wire
     */
    String getName();
}
