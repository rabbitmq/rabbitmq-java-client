package com.rabbitmq.client.impl.recovery;

import java.io.IOException;

/**
 * @since 3.3.0
 */
public class RecordedExchangeBinding extends RecordedBinding {
    public RecordedExchangeBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    public void recover() throws IOException {
        this.channel.getDelegate().exchangeBind(this.destination, this.source, this.routingKey, this.arguments);
    }
}
