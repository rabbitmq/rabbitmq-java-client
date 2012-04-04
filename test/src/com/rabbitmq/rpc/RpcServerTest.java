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

import junit.framework.TestCase;

import com.rabbitmq.client.Channel;

/**
 * Unit test the class {@link RpcServer}
 */
public class RpcServerTest extends TestCase {

    /** channel for tests */
    private final Channel channel = new StubChannel();
    /** rpcServer for tests */
    private final RpcServer rpcServer = RpcServer.newServer(this.channel);

    /**
     * Tests {@link RpcServer#newServer(Channel) RpcServer} static factory with no errors
     * @throws Exception test
     */
    public void testConstruction() throws Exception {
        assertEquals("Should be no processors initially", 0,
                numberOfProcessors(this.rpcServer));
    }

    /**
     * Tests {@link RpcServer#startProcessor(RpcProcessor) startProcessor()}
     * @throws Exception test
     */
    public void testStartProcessor() throws Exception {
        StubRpcProcessor rpcp;
        this.rpcServer.startProcessor(rpcp = new StubRpcProcessor());
        assertTrue("start not called on processor, first time",
                rpcp.startCalled);
        assertFalse("stop called on processor, first time", rpcp.stopCalled);
        assertEquals("Should be one processor after one start", 1,
                numberOfProcessors(this.rpcServer));
        this.rpcServer.startProcessor(rpcp = new StubRpcProcessor());
        assertTrue("start not called on processor, second time",
                rpcp.startCalled);
        assertFalse("stop called on processor, second time", rpcp.stopCalled);
        assertEquals("Should be two processors after two start", 2,
                numberOfProcessors(this.rpcServer));
    }

    /**
     * Tests {@link RpcServer#startProcessor(RpcProcessor) startProcessor()} duplicate
     * @throws Exception test
     */
    public void testStartProcessorDuplicate() throws Exception {
        StubRpcProcessor rpcp;
        this.rpcServer.startProcessor(rpcp = new StubRpcProcessor());
        assertEquals("Should be one processor after one start", 1,
                numberOfProcessors(this.rpcServer));
        rpcp.startCalled = false; // (partial reset)
        try {
            this.rpcServer.startProcessor(rpcp);
            fail("Should throw exception on duplicate processor");
        } catch (Exception e) {
            assertEquals("Wrong class for exception", "IOException", e
                    .getClass().getSimpleName());
        }
        assertFalse("start called on processor, second time", rpcp.startCalled);
        assertFalse("stop called on processor, second time", rpcp.stopCalled);
        assertEquals("Should be one processor after trying duplicate", 1,
                numberOfProcessors(this.rpcServer));
    }

    /**
     * Test close of many processored (2) rpcServer
     * @throws Exception test
     */
    public void testCloseMany() throws Exception {
        StubRpcProcessor rpcp1 = new StubRpcProcessor();
        this.rpcServer.startProcessor(rpcp1);
        StubRpcProcessor rpcp2 = new StubRpcProcessor();
        this.rpcServer.startProcessor(rpcp2);
        this.rpcServer.close();
        assertTrue("stop not called on processor 1", rpcp1.stopCalled);
        assertTrue("stop not called on processor 2", rpcp2.stopCalled);
        assertEquals("Should be empty after close()", 0,
                numberOfProcessors(this.rpcServer));
    }

    /**
     * Test close of empty rpcServer
     * @throws Exception test
     */
    public void testCloseEmpty() throws Exception {
        this.rpcServer.close();
        assertEquals("Should (still) be empty after close()", 0,
                numberOfProcessors(this.rpcServer));
    }

    /**
     * Test double close of empty rpcServer
     * @throws Exception test
     */
    public void testCloseDouble() throws Exception {
        this.rpcServer.close();
        try {
            this.rpcServer.close();
        } catch (Exception e) {
            assertEquals("Wrong class for exception", "IOException",
                    e.getClass().getSimpleName());
        }
        assertEquals("Should (still) be empty after double close()", 0,
                numberOfProcessors(this.rpcServer));
    }

    private static int numberOfProcessors(RpcServer rpcServer) {
        int count = 0;
        for (@SuppressWarnings("unused")
        RpcProcessor p : rpcServer) {
            count++;
        }
        return count;
    }
}
