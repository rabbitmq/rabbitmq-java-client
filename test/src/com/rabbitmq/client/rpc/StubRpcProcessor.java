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

import com.rabbitmq.client.Channel;

/**
 * Stub for {@link RpcProcessor} objects for testing.
 */
public class StubRpcProcessor implements RpcProcessor {

    private IOException throwException = null;
    boolean startCalled = false;
    boolean stopCalled = false;
    /**
     * Create a stub {@link RpcProcessor} for tests
     */
    public StubRpcProcessor() {}

    void reset() {
        this.startCalled = false;
        this.stopCalled = false;
        this.throwException = null;
    }

    void setException(IOException e) {
        this.throwException = e;
    }

    public void start(Channel channel) throws IOException {
        if (this.throwException != null) throw this.throwException;
        this.startCalled = true;
    }

    public void stop() throws IOException {
        if (this.throwException != null) throw this.throwException;
        this.stopCalled = true;
    }

}
