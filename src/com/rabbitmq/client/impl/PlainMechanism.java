package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;

/**
 * The PLAIN auth mechanism
 */
public class PlainMechanism implements AuthMechanism {
    public AMQP.Connection.Tune doLogin(AMQChannel channel,
                                        ConnectionFactory factory) throws IOException {
        LongString saslResponse = LongStringHelper.asLongString(
                "\0" + factory.getUsername() + "\0" + factory.getPassword());
        AMQImpl.Connection.StartOk startOk =
            new AMQImpl.Connection.StartOk(factory.getClientProperties(), getName(),
                                           saslResponse, "en_US");

        try {
            return (AMQP.Connection.Tune) channel.rpc(startOk).getMethod();
        } catch (ShutdownSignalException e) {
            throw AMQChannel.wrap(e, "Possibly caused by authentication failure");
        }
    }

    public String getName() {
        return "PLAIN";
    }
}
