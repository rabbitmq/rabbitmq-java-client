package com.rabbitmq.client.impl.recovery;

import java.io.IOException;

/**
 * @since 3.3.0
 */
public class RecordedQueueBinding extends RecordedBinding {
    public RecordedQueueBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    public void recover() throws IOException {
        this.channel.getDelegate().queueBind(this.getDestination(), this.getSource(), this.routingKey, this.arguments);
    }
}
