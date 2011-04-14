package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.functional.ClusteredTestBase;
import com.rabbitmq.tools.Host;

import java.io.IOException;

/**
 *
 */
public class ServerUtil {
    public static void restart(BrokerTestCase testCase) throws IOException
    {
        testCase.tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
        testCase.setUp();
    }

    public static void restartCluster(ClusteredTestBase testCase) throws IOException {
        if (testCase.clusteredConnection != null) {
            testCase.clusteredConnection.abort();
            testCase.clusteredConnection = null;
            testCase.clusteredChannel = null;
            testCase.alternateConnection = null;
            testCase.alternateChannel = null;

            Host.executeCommand("cd ../rabbitmq-test; make restart-secondary-node");
        }
        restart(testCase);
    }
}
