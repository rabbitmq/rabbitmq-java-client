package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * See bug 21846:
 * Basic.Ack is now required to signal a channel error immediately upon
 * detecting an invalid deliveryTag, even if the channel is (Tx-)transacted.
 *
 * Specifically, a client MUST not acknowledge the same message more than once.
 */
public abstract class InvalidAcksBase extends BrokerTestCase {
    protected abstract void select() throws IOException;
    protected abstract void commit() throws IOException;

    public void testDoubleAck()
        throws IOException
    {
        select();
        String q = channel.queueDeclare().getQueue();
        basicPublishVolatile(q);
        commit();

        long tag = channel.basicGet(q, false).getEnvelope().getDeliveryTag();
        channel.basicAck(tag, false);
        channel.basicAck(tag, false);

        expectChannelError(AMQP.PRECONDITION_FAILED);
    }

    public void testCrazyAck()
        throws IOException
    {
        select();
        channel.basicAck(123456, false);
        expectChannelError(AMQP.PRECONDITION_FAILED);
    }
}
