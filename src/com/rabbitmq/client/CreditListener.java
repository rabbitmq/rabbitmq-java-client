package com.rabbitmq.client;

import java.io.IOException;

/**
 *
 */
public interface CreditListener {
    void handleCredit(long credit, long available, boolean drain)
        throws IOException;

}
