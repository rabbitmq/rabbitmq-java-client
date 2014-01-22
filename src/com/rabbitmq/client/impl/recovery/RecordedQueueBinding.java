package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.AMQP;

import java.io.IOException;

/**
 * @since 3.3.0
 */
public class RecordedQueueBinding extends RecordedBinding {
    public RecordedQueueBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    public AMQP.Queue.BindOk recover() throws IOException {
        return this.channel.getDelegate().queueBind(this.getDestination(), this.getSource(), this.routingKey, this.arguments);
    }
}
