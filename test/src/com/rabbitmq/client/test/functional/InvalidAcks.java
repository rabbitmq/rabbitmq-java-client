package com.rabbitmq.client.test.functional;

import java.io.IOException;

public class InvalidAcks extends InvalidAcksBase {
    protected void select() throws IOException {}
    protected void commit() throws IOException {}
}

