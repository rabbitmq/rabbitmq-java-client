package com.rabbitmq.client.test.server;

import java.io.IOException;

public class PersisterRestart6 extends RestartBase {

    private static final String q = "Restart6";
    
    public void testRestart() throws IOException, InterruptedException {
        declareDurableQueue(q);
        basicPublishPersistent("a".getBytes(), q);
        basicPublishPersistent("b".getBytes(), q);
        basicPublishPersistent("c".getBytes(), q);
        restart();
        assertTrue(new String(basicGet(q).getBody()).equals("a"));
        restart();
        restart();
        assertTrue(new String(basicGet(q).getBody()).equals("b"));
        assertTrue(new String(basicGet(q).getBody()).equals("c"));
        deleteQueue(q);
    }
    
}
