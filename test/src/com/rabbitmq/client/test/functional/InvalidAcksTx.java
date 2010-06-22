package com.rabbitmq.client.test.functional;

import java.io.IOException;

public class InvalidAcksTx extends InvalidAcksBase {
    protected void select() throws IOException {
        channel.txSelect();
    }

    protected void commit() throws IOException {
        channel.txCommit();
    }
}
