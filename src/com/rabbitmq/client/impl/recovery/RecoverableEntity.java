package com.rabbitmq.client.impl.recovery;

import java.io.IOException;

/**
 * @since 3.3.0
 */
public interface RecoverableEntity {
    Object recover() throws IOException;
}
