package com.rabbitmq.client.impl.recovery;

import java.io.*;

public class RecordedQueueBinding extends RecordedBinding implements RecoverableEntity {
    public RecordedQueueBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    public Object recover() throws IOException {
        return this.channel.getDelegate().queueBind(this.getDestination(), this.getSource(), this.routingKey, this.arguments);
    }
}
