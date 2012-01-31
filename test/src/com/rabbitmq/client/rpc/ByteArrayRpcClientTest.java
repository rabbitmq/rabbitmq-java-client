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
package com.rabbitmq.client.rpc;

import java.io.IOException;

import junit.framework.TestCase;

/**
 * Tests for {@link ByteArrayRpcClient}
 */
public class ByteArrayRpcClientTest extends TestCase {

    private final StubChannel stubChannel = new StubChannel();
    private final ByteArrayRpcClient baRpcClient = new ByteArrayRpcClient(this.stubChannel);

    private static final String exchange = "exchange";
    private static final String routingKey = "routingKey";
    private static final byte[] message = new byte[]{1,2,3,4,5};

    /**
     * Test open() basic
     * @throws Exception test
     */
    public void testOpen() throws Exception {

    }
    /**
     * Test <code>call(...)</code> before open
     * @throws Exception test
     */
    public void testCallBeforeOpen() throws Exception {
        try {
            this.baRpcClient.call(exchange, routingKey, message);
            fail("Should throw exception");
        } catch (Exception e) {
            assertTrue("Call before open expected IOException", e instanceof IOException);
        }
    }
    /**
     * Test <code>call(...)</code> after close
     * @throws Exception test
     */
    public void testCallAfterClose() throws Exception {
        this.baRpcClient.close();
        try {
            this.baRpcClient.call(exchange, routingKey, message);
            fail("Should throw exception");
        } catch (Exception e) {
            assertTrue("Call after close expected IOException", e instanceof IOException);
        }
    }
    /**
     * Test close() basic
     * @throws Exception test
     */
    public void testClose() throws Exception {

    }
}
