package com.rabbitmq.client.impl.recovery;

import java.io.*;

public class RecordedExchangeBinding extends RecordedBinding implements RecoverableEntity {
    public RecordedExchangeBinding(RecoveringChannel channel) {
        super(channel);
    }

    public Object recover() throws IOException {
        return this.channel.getDelegate().exchangeBind(this.source, this.destination, this.routingKey, this.arguments);
    }
}
