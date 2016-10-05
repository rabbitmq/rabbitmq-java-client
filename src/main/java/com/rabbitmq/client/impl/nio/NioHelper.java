package com.rabbitmq.client.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;

/**
 * Created by acogoluegnes on 04/10/2016.
 */
public class NioHelper {

    static int read(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        int read = channel.read(buffer);
        if(read < 0) {
            throw new IOException("Channel has reached EOF");
        }
        return read;
    }

}
