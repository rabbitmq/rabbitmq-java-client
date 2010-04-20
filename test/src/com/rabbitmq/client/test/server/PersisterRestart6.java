package com.rabbitmq.client.test.server;

import java.io.IOException;

public class PersisterRestart6 extends RestartBase {

    private static final String utf8 = "UTF-8";
    private static final String q = "Restart6";
    
    public void testRestart() throws IOException, InterruptedException {
        declareDurableQueue(q);
        basicPublishPersistent("a".getBytes(utf8), q);
        basicPublishPersistent("b".getBytes(utf8), q);
        basicPublishPersistent("c".getBytes(utf8), q);
        forceSnapshot();
        restart();
        assertTrue(new String(basicGet(q).getBody(), utf8).equals("a"));
        forceSnapshot();
        restart();
        restart();
        assertTrue(new String(basicGet(q).getBody(), utf8).equals("b"));
        assertTrue(new String(basicGet(q).getBody(), utf8).equals("c"));
        deleteQueue(q);
    }
    
}
