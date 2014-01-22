package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.AMQP;

import java.io.IOException;

/**
 * @since 3.3.0
 */
public class RecordedExchangeBinding extends RecordedBinding {
    public RecordedExchangeBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    public AMQP.Exchange.BindOk recover() throws IOException {
        return this.channel.getDelegate().exchangeBind(this.source, this.destination, this.routingKey, this.arguments);
    }
}
