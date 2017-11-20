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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.impl.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Class to create AMQP frames from a {@link ReadableByteChannel}.
 * Supports partial frames: a frame can be read in several attempts
 * from the {@link NioLoop}. This can happen when the channel won't
 * read any more bytes in the middle of a frame building. The state
 * of the outstanding frame is saved up, and the builder will
 * start where it left off when the {@link NioLoop} comes back to
 * this connection.
 * This class is not thread safe.
 * @since 4.4.0
 */
public class FrameBuilder {

    private static final int PAYLOAD_OFFSET = 1 /* type */ + 2 /* channel */ + 4 /* payload size */;

    protected final ReadableByteChannel channel;

    protected final ByteBuffer applicationBuffer;

    private int frameType;
    private int frameChannel;
    private byte [] framePayload;

    private int bytesRead = 0;

    // to store the bytes of the outstanding data
    // 3 byte-long because the longest we read is an unsigned int
    // (not need to store the latest byte)
    private final int [] frameBuffer = new int[3];

    public FrameBuilder(ReadableByteChannel channel, ByteBuffer buffer) {
        this.channel = channel;
        this.applicationBuffer = buffer;
    }

    /**
     * Read a frame from the network.
     * This method returns null f a frame could not have been fully built from
     * the network. The client must then retry later (typically
     * when the channel notifies it has something to read).
     * @return a complete frame or null if a frame couldn't have been fully built
     * @throws IOException
     */
    public Frame readFrame() throws IOException {
        while(somethingToRead()) {
            if (bytesRead == 0) {
                // type
                // FIXME check first byte isn't 'A' and thus a header indicating protocol version mismatch
                frameType = readFromBuffer();
            } else if (bytesRead == 1) {
                // channel 1/2
                frameBuffer[0] = readFromBuffer();
            } else if (bytesRead == 2) {
                // channel 2/2
                frameChannel = (frameBuffer[0] << 8) + (readFromBuffer() << 0);
            } else if (bytesRead == 3) {
                // payload size 1/4
                frameBuffer[0] = readFromBuffer();
            } else if (bytesRead == 4) {
                // payload size 2/4
                frameBuffer[1] = readFromBuffer();
            } else if (bytesRead == 5) {
                // payload size 3/4
                frameBuffer[2] = readFromBuffer();
            } else if (bytesRead == 6) {
                // payload size 4/4
                int framePayloadSize = ((frameBuffer[0] << 24) + (frameBuffer[1] << 16) + (frameBuffer[2] << 8) + (readFromBuffer() << 0));
                framePayload = new byte[framePayloadSize];
            } else if (bytesRead >= PAYLOAD_OFFSET && bytesRead < framePayload.length + PAYLOAD_OFFSET) {
                framePayload[bytesRead - PAYLOAD_OFFSET] = (byte) readFromBuffer();
            } else if (bytesRead == framePayload.length + PAYLOAD_OFFSET) {
                int frameEndMarker = readFromBuffer();
                if (frameEndMarker != AMQP.FRAME_END) {
                    throw new MalformedFrameException("Bad frame end marker: " + frameEndMarker);
                }
                bytesRead = 0;
                return new Frame(frameType, frameChannel, framePayload);
            } else {
                throw new IllegalStateException("Number of read bytes incorrect: " + bytesRead);
            }
            bytesRead++;
        }
        return null;
    }

    private int read() throws IOException {
        return NioHelper.read(channel, applicationBuffer);
    }

    private int readFromBuffer() {
        return applicationBuffer.get() & 0xff;
    }

    /**
     * Tells whether there's something to read in the application buffer or not.
     * Tries to read from the network if necessary.
     * @return true if there's something to read in the application buffer
     * @throws IOException
     */
    protected boolean somethingToRead() throws IOException {
        if(!applicationBuffer.hasRemaining()) {
            applicationBuffer.clear();
            int read = read();
            applicationBuffer.flip();
            if (read > 0) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }
}
