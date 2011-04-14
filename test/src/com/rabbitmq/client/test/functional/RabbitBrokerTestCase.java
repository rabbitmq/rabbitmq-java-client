package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;

public class RabbitBrokerTestCase extends BrokerTestCase {
    protected void restart()
        throws IOException
    {
        tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }
        setUp();
    }
}
