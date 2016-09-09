package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.Frame;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.*;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class FrameTest {

    @Test public void readFrames() throws IOException {
        Random random = new Random();
        int nbOfFrames = 100;
        AccumulatorReadableByteChannel channel = new AccumulatorReadableByteChannel();

        for(int i = 0; i < nbOfFrames; i++) {
            byte[] payload = new byte[random.nextInt(2000) + 1];
            Frame frame = new Frame(AMQP.FRAME_METHOD, 1, payload);
            channel.add(frame);
        }

        ByteBuffer buffer = ByteBuffer.allocate(8192);

        int nbReadFrames = 0;
        channel.read(buffer);
        buffer.flip();
        while(buffer.hasRemaining()) {
            Frame.readFrom(channel, buffer);
            nbReadFrames++;
            if(!buffer.hasRemaining()) {
                buffer.clear();
                channel.read(buffer);
                buffer.flip();
            }

        }
        assertThat(nbReadFrames, equalTo(nbOfFrames));
    }

    @Test public void readLargeFrame() throws IOException {
        AccumulatorReadableByteChannel channel = new AccumulatorReadableByteChannel();

        int [] framesSize = new int [] {100, 75, 20000, 150};
        for (int frameSize : framesSize) {
            Frame frame = new Frame(AMQP.FRAME_METHOD, 1, new byte[frameSize]);
            channel.add(frame);
        }

        ByteBuffer buffer = ByteBuffer.allocate(8192);

        int nbReadFrames = 0;
        channel.read(buffer);
        buffer.flip();
        while(buffer.hasRemaining()) {
            Frame.readFrom(channel, buffer);
            nbReadFrames++;
            if(!buffer.hasRemaining()) {
                buffer.clear();
                channel.read(buffer);
                buffer.flip();
            }

        }
        assertThat(nbReadFrames, equalTo(framesSize.length));
    }

    @Test
    public void writeFrames() throws IOException {
        List<Frame> frames = new ArrayList<Frame>();
        Random random = new Random();
        int totalFrameSize = 0;
        for(int i = 0; i < 100; i++) {
            byte[] payload = new byte[random.nextInt(2000) + 1];
            Frame frame = new Frame(AMQP.FRAME_METHOD, 1, payload);
            frames.add(frame);
            totalFrameSize += frame.size();
        }

        AccumulatorWritableByteChannel channel = new AccumulatorWritableByteChannel();
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        for (Frame frame : frames) {
            frame.writeTo(channel, buffer);
        }
        Frame.drain(channel, buffer);
        checkWrittenChunks(totalFrameSize, channel);
    }

    @Test public void writeLargeFrame() throws IOException {
        List<Frame> frames = new ArrayList<Frame>();
        int totalFrameSize = 0;
        int [] framesSize = new int [] {100, 75, 20000, 150};
        for (int frameSize : framesSize) {
            Frame frame = new Frame(AMQP.FRAME_METHOD, 1, new byte[frameSize]);
            frames.add(frame);
            totalFrameSize += frame.size();
        }

        AccumulatorWritableByteChannel channel = new AccumulatorWritableByteChannel();
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        for (Frame frame : frames) {
            frame.writeTo(channel, buffer);
        }
        Frame.drain(channel, buffer);
        checkWrittenChunks(totalFrameSize, channel);
    }

    private void checkWrittenChunks(int totalFrameSize, AccumulatorWritableByteChannel channel) {
        int totalWritten  = 0;
        for (byte[] chunk : channel.chunks) {
            totalWritten += chunk.length;
        }
        assertThat(totalWritten, equalTo(totalFrameSize));
    }

    private static class AccumulatorWritableByteChannel implements WritableByteChannel {

        List<byte[]> chunks = new ArrayList<byte[]>();

        Random random = new Random();

        @Override
        public int write(ByteBuffer src) throws IOException {
            int remaining = src.remaining();
            if(remaining > 0) {
                int toRead = random.nextInt(remaining) + 1;
                byte [] chunk = new byte[toRead];
                src.get(chunk);
                chunks.add(chunk);
                return toRead;
            } else {
                return remaining;
            }

        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() throws IOException {

        }
    }

    private static class AccumulatorReadableByteChannel implements ReadableByteChannel {

        private List<Byte> bytesOfFrames = new LinkedList<Byte>();

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int remaining = dst.remaining();
            int read = 0;
            if(remaining > 0) {
                Iterator<Byte> iterator = bytesOfFrames.iterator();
                while(iterator.hasNext() && read < remaining) {
                    dst.put(iterator.next());
                    iterator.remove();
                    read++;
                }
            }
            return read;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() throws IOException {

        }

        void add(Frame frame) throws IOException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(frame.size());
            DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream);
            frame.writeTo(outputStream);
            outputStream.flush();
            for (byte b : byteArrayOutputStream.toByteArray()) {
                bytesOfFrames.add(b);
            }
        }
    }

}
