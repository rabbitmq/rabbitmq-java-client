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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Bridge between the byte buffer and stream worlds.
 */
public class SslEngineByteBufferOutputStream extends OutputStream {

    private final SSLEngine sslEngine;

    private final ByteBuffer plainOut, cypherOut;

    private final WritableByteChannel channel;

    public SslEngineByteBufferOutputStream(SSLEngine sslEngine, ByteBuffer plainOut, ByteBuffer cypherOut, WritableByteChannel channel) {
        this.sslEngine = sslEngine;
        this.plainOut = plainOut;
        this.cypherOut = cypherOut;
        this.channel = channel;
    }

    @Override
    public void write(int b) throws IOException {
        if (!plainOut.hasRemaining()) {
            doFlush();
        }
        plainOut.put((byte) b);
    }

    @Override
    public void flush() throws IOException {
        if (plainOut.position() > 0) {
            doFlush();
        }
    }

    private void doFlush() throws IOException {
        plainOut.flip();
        SslEngineHelper.write(channel, sslEngine, plainOut, cypherOut);
        plainOut.clear();
    }
}
