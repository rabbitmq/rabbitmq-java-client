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

package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.nio.FrameBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class FrameBuilderTest {

    @Mock
    ReadableByteChannel channel;

    ByteBuffer buffer;

    FrameBuilder builder;

    @Test
    public void buildFrameInOneGo() throws IOException {
        buffer = ByteBuffer.wrap(new byte[]{1, 0, 0, 0, 0, 0, 3, 1, 2, 3, end()});
        builder = new FrameBuilder(channel, buffer);
        Frame frame = builder.readFrame();
        assertThat(frame, notNullValue());
        assertThat(frame.type, is(1));
        assertThat(frame.channel, is(0));
        assertThat(frame.getPayload().length, is(3));
    }

    @Test
    public void buildFramesInOneGo() throws IOException {
        byte[] frameContent = new byte[]{1, 0, 0, 0, 0, 0, 3, 1, 2, 3, end()};
        int nbFrames = 13;
        byte[] frames = new byte[frameContent.length * nbFrames];
        for(int i = 0; i < nbFrames; i++) {
            for (int j = 0; j < frameContent.length; j++) {
                frames[i * frameContent.length + j] = frameContent[j];
            }
        }
        buffer = ByteBuffer.wrap(frames);
        builder = new FrameBuilder(channel, buffer);
        int frameCount = 0;
        Frame frame;
        while ((frame = builder.readFrame()) != null) {
            assertThat(frame, notNullValue());
            assertThat(frame.type, is(1));
            assertThat(frame.channel, is(0));
            assertThat(frame.getPayload().length, is(3));
            frameCount++;
        }
        assertThat(frameCount, is(nbFrames));
    }

    @Test
    public void buildFrameInSeveralCalls() throws IOException {
        buffer = ByteBuffer.wrap(new byte[]{1, 0, 0, 0, 0, 0, 3, 1, 2});
        builder = new FrameBuilder(channel, buffer);
        Frame frame = builder.readFrame();
        assertThat(frame, nullValue());

        buffer.clear();
        buffer.put(b(3)).put(end());
        buffer.flip();

        frame = builder.readFrame();
        assertThat(frame, notNullValue());
        assertThat(frame.type, is(1));
        assertThat(frame.channel, is(0));
        assertThat(frame.getPayload().length, is(3));
    }

    byte b(int b) {
        return (byte) b;
    }

    byte end() {
        return (byte) AMQP.FRAME_END;
    }

}
