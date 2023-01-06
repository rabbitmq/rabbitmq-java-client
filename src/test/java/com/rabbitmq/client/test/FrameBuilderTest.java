// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.nio.FrameBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 *
 */
public class FrameBuilderTest {

    @Mock
    ReadableByteChannel channel;

    ByteBuffer buffer;

    FrameBuilder builder;

    AutoCloseable mocks;

    @BeforeEach
    void init() {
        this.mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void buildFrameInOneGo() throws IOException {
        buffer = ByteBuffer.wrap(new byte[] { 1, 0, 0, 0, 0, 0, 3, 1, 2, 3, end() });
        builder = new FrameBuilder(channel, buffer);
        Frame frame = builder.readFrame();
        assertThat(frame).isNotNull();
        assertThat(frame.getType()).isEqualTo(1);
        assertThat(frame.getChannel()).isEqualTo(0);
        assertThat(frame.getPayload()).hasSize(3);
    }

    @Test
    public void buildFramesInOneGo() throws IOException {
        byte[] frameContent = new byte[] { 1, 0, 0, 0, 0, 0, 3, 1, 2, 3, end() };
        int nbFrames = 13;
        byte[] frames = new byte[frameContent.length * nbFrames];
        for (int i = 0; i < nbFrames; i++) {
            for (int j = 0; j < frameContent.length; j++) {
                frames[i * frameContent.length + j] = frameContent[j];
            }
        }
        buffer = ByteBuffer.wrap(frames);
        builder = new FrameBuilder(channel, buffer);
        int frameCount = 0;
        Frame frame;
        while ((frame = builder.readFrame()) != null) {
            assertThat(frame).isNotNull();
            assertThat(frame.getType()).isEqualTo(1);
            assertThat(frame.getChannel()).isEqualTo(0);
            assertThat(frame.getPayload()).hasSize(3);
            frameCount++;
        }
        assertThat(frameCount).isEqualTo(nbFrames);
    }

    @Test
    public void buildFrameInSeveralCalls() throws IOException {
        buffer = ByteBuffer.wrap(new byte[] { 1, 0, 0, 0, 0, 0, 3, 1, 2 });
        builder = new FrameBuilder(channel, buffer);
        Frame frame = builder.readFrame();
        assertThat(frame).isNull();

        buffer.clear();
        buffer.put(b(3)).put(end());
        buffer.flip();

        frame = builder.readFrame();
        assertThat(frame).isNotNull();
        assertThat(frame.getType()).isEqualTo(1);
        assertThat(frame.getChannel()).isEqualTo(0);
        assertThat(frame.getPayload()).hasSize(3);
    }

    @Test
    public void protocolMismatchHeader() throws IOException {
        ByteBuffer[] buffers = new ByteBuffer[] {
            ByteBuffer.wrap(new byte[] { 'A' }),
            ByteBuffer.wrap(new byte[] { 'A', 'M', 'Q' }),
            ByteBuffer.wrap(new byte[] { 'A', 'N', 'Q', 'P' }),
            ByteBuffer.wrap(new byte[] { 'A', 'M', 'Q', 'P' }),
            ByteBuffer.wrap(new byte[] { 'A', 'M', 'Q', 'P', 1, 1, 8 }),
            ByteBuffer.wrap(new byte[] { 'A', 'M', 'Q', 'P', 1, 1, 8, 0 }),
            ByteBuffer.wrap(new byte[] { 'A', 'M', 'Q', 'P', 1, 1, 9, 1 })
        };
        String[] messages = new String[] {
            "Invalid AMQP protocol header from server: read only 1 byte(s) instead of 4",
            "Invalid AMQP protocol header from server: read only 3 byte(s) instead of 4",
            "Invalid AMQP protocol header from server: expected character 77, got 78",
            "Invalid AMQP protocol header from server",
            "Invalid AMQP protocol header from server",
            "AMQP protocol version mismatch; we are version 0-9-1, server is 0-8",
            "AMQP protocol version mismatch; we are version 0-9-1, server sent signature 1,1,9,1"
        };

        for (int i = 0; i < buffers.length; i++) {
            builder = new FrameBuilder(channel, buffers[i]);
            try {
                builder.readFrame();
                fail("protocol header not correct, exception should have been thrown");
            } catch (MalformedFrameException e) {
                assertThat(e.getMessage()).isEqualTo(messages[i]);
            }
        }
    }

    byte b(int b) {
        return (byte) b;
    }

    byte end() {
        return (byte) AMQP.FRAME_END;
    }
}
