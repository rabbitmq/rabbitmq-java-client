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
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Sub-class of {@link FrameBuilder} that unwraps crypted data from the network.
 * @since 4.4.0
 */
public class SslEngineFrameBuilder extends FrameBuilder {

    private final SSLEngine sslEngine;

    private final ByteBuffer cipherBuffer;

    public SslEngineFrameBuilder(SSLEngine sslEngine, ByteBuffer plainIn, ByteBuffer cipherIn, ReadableByteChannel channel) {
        super(channel, plainIn);
        this.sslEngine = sslEngine;
        this.cipherBuffer = cipherIn;
    }

    @Override
    protected boolean somethingToRead() throws IOException {
        if (applicationBuffer.hasRemaining()) {
            return true;
        } else {
            applicationBuffer.clear();

            while (true) {
                SSLEngineResult result = sslEngine.unwrap(cipherBuffer, applicationBuffer);
                switch (result.getStatus()) {
                    case OK:
                        applicationBuffer.flip();
                        if (applicationBuffer.hasRemaining()) {
                            return true;
                        }
                        applicationBuffer.clear();
                        break;
                    case BUFFER_OVERFLOW:
                        throw new SSLException("buffer overflow in read");
                    case BUFFER_UNDERFLOW:
                        cipherBuffer.compact();
                        int read = NioHelper.read(channel, cipherBuffer);
                        if (read == 0) {
                            return false;
                        }
                        cipherBuffer.flip();
                        break;
                    case CLOSED:
                        throw new SSLException("closed in read");
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
            }
        }
    }

}
