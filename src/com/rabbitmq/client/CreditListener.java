package com.rabbitmq.client;

import java.io.IOException;

/**
 *
 */
public interface CreditListener {
    void handleCredit(String consumerTag, long credit, long available, boolean drain)
        throws IOException;

}
