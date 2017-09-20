// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.impl.nio;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Bridge between the byte buffer and stream worlds.
 */
public class SslEngineByteBufferInputStream extends InputStream {

    private final SSLEngine sslEngine;

    private final ByteBuffer plainIn, cipherIn;

    private final ReadableByteChannel channel;

    public SslEngineByteBufferInputStream(SSLEngine sslEngine, ByteBuffer plainIn, ByteBuffer cipherIn, ReadableByteChannel channel) {
        this.sslEngine = sslEngine;
        this.plainIn = plainIn;
        this.cipherIn = cipherIn;
        this.channel = channel;
    }

    @Override
    public int read() throws IOException {
        if (plainIn.hasRemaining()) {
            return readFromBuffer(plainIn);
        } else {
            plainIn.clear();
            SSLEngineResult result = sslEngine.unwrap(cipherIn, plainIn);

            switch (result.getStatus()) {
            case OK:
                plainIn.flip();
                if (plainIn.hasRemaining()) {
                    return readFromBuffer(plainIn);
                }
                break;
            case BUFFER_OVERFLOW:
                throw new SSLException("buffer overflow in read");
            case BUFFER_UNDERFLOW:
                if (cipherIn.hasRemaining()) {
                    cipherIn.compact();
                } else {
                    cipherIn.clear();
                }

                int bytesRead = NioHelper.read(channel, cipherIn);
                if (bytesRead > 0) {
                    cipherIn.flip();
                } else {
                    bytesRead = NioHelper.retryRead(channel, cipherIn);
                    if(bytesRead <= 0) {
                        throw new IllegalStateException("Should be reading something from the network");
                    }
                    // see https://github.com/rabbitmq/rabbitmq-java-client/issues/307
                    cipherIn.flip();
                }
                plainIn.clear();
                result = sslEngine.unwrap(cipherIn, plainIn);

                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    throw new SSLException("Unexpected result: " + result);
                }
                plainIn.flip();
                if (plainIn.hasRemaining()) {
                    return readFromBuffer(plainIn);
                }
                break;
            case CLOSED:
                throw new SSLException("closed in read");
            default:
                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }

        return -1;
    }

    private int readFromBuffer(ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }
}
