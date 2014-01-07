package com.rabbitmq.client.impl.recovery;

import java.io.IOException;

public interface RecoverableEntity {
    Object recover() throws IOException;
}
