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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * Test stub for {@link ByteArrayRpcClient}
 */
public class StubByteArrayRpcClient implements RpcClient<byte[], byte[]> {

    private Exception savedException = null;
    private byte[] resultBytes;

    /**
     * Override exception in operations
     * @param exception to throw
     */
    public void setException(Exception exception) {
        this.savedException = exception;
    }

    /**
     * Set result of call()
     * @param result of call()
     */
    public void setResult(byte[] result) {
        this.resultBytes = result;
    }

    public void open() throws IOException {
        throwIfNecessary();
    }

    private void throwIfNecessary() throws IOException {
        if (this.savedException != null) {
            if (this.savedException instanceof RuntimeException)
                throw (RuntimeException) this.savedException;
            if (this.savedException instanceof IOException)
                throw (IOException) this.savedException;
            throw new RuntimeException("Invalid exception in stub", this.savedException);
        }
    }

    public byte[] call(String exchange, String routingKey, byte[] parameter)
            throws IOException, ShutdownSignalException, TimeoutException {
        throwIfNecessary();
        return this.resultBytes;
    }

    public void close() throws IOException {
        throwIfNecessary();
    }

}
