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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Bridge between the byte buffer and stream worlds.
 */
public class ByteBufferInputStream extends InputStream {

    private final ReadableByteChannel channel;

    private final ByteBuffer buffer;

    public ByteBufferInputStream(ReadableByteChannel channel, ByteBuffer buffer) {
        this.channel = channel;
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        readFromNetworkIfNecessary(channel, buffer);
        return readFromBuffer(buffer);
    }

    private int readFromBuffer(ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }

    private static void readFromNetworkIfNecessary(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        if(!buffer.hasRemaining()) {
            buffer.clear();
            int read = NioHelper.read(channel, buffer);
            if(read <= 0) {
                NioHelper.retryRead(channel, buffer);
            }
            buffer.flip();
        }
    }
}
