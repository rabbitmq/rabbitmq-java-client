//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2012 VMware, Inc.  All rights reserved.
//
package com.rabbitmq.rpc;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests for {@link ByteArrayRpcClient}
 */
public class ByteArrayRpcClientTest extends TestCase {

    private final StubChannel stubChannel = new StubChannel(resultMap);
    private final ByteArrayRpcClient baRpcClient = new ByteArrayRpcClient(this.stubChannel);

    private static final String exchange = "exchange";
    private static final String routingKey = "routingKey";
    private static final byte[] testMessage = new byte[]{1,2,3,4,5};
    private static final byte[] testReplyMessage = new byte[]{5,4,3,2,1};

    private static <K,V> Map<K,V> newMap() { return new HashMap<K,V>(); }

    private static final Map<byte[], byte[]> resultMap = newMap();
    static {
        resultMap.put(testMessage, testReplyMessage);
    }

    /**
     * Test open() basic
     * @throws Exception test
     */
    public void testOpen() throws Exception {
        this.baRpcClient.open();
    }
    /**
     * Test <code>call(...)</code> before open
     * @throws Exception test
     */
    public void testCallBeforeOpen() throws Exception {
        try {
            this.baRpcClient.call(exchange, routingKey, testMessage);
            fail("Should throw exception");
        } catch (Exception e) {
            assertTrue("Call before open expected EOFException", e instanceof EOFException);
        }
    }
    /**
     * Test <code>call(...)</code> after close
     * @throws Exception test
     */
    public void testCallAfterClose() throws Exception {
        this.baRpcClient.open();
        this.baRpcClient.close();
        try {
            this.baRpcClient.call(exchange, routingKey, testMessage);
            fail("Should throw exception");
        } catch (Exception e) {
            assertTrue("Call after close expected EOFException", e instanceof EOFException);
        }
    }

    private static void assertArrayEquals(String msg, byte[] exp, byte[] act) {
        assertEquals(msg + " (incorrect size)", exp.length, act.length);
        for (int i=0; i<exp.length; i++) {
            assertEquals(msg + " (index "+i+")", exp[i], act[i]);
        }
    }

    /**
     * Test call() with argument
     * @throws Exception test
     */
    public void testCallStandard() throws Exception {
        this.baRpcClient.open();
        assertArrayEquals("Incorrect returned value", testReplyMessage, this.baRpcClient.call(exchange, routingKey, testMessage));
        this.baRpcClient.close();
    }

    /**
     * Test close() as very first action
     * @throws Exception test
     */
    public void testCloseNoop() throws Exception {
        this.baRpcClient.close();
    }
}
