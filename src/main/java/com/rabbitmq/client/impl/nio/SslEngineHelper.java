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
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;

/**
 *
 */
public class SslEngineHelper {

    public static boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {

        ByteBuffer plainOut = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer plainIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer cipherOut = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer cipherIn = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != FINISHED && handshakeStatus != NOT_HANDSHAKING) {
            switch (handshakeStatus) {
            case NEED_TASK:
                handshakeStatus = runDelegatedTasks(engine);
                break;
            case NEED_UNWRAP:
                handshakeStatus = unwrap(cipherIn, plainIn, socketChannel, engine);
                break;
            case NEED_WRAP:
                handshakeStatus = wrap(plainOut, cipherOut, socketChannel, engine);
                break;
            }
        }
        return true;
    }

    private static SSLEngineResult.HandshakeStatus runDelegatedTasks(SSLEngine sslEngine) {
        // FIXME run in executor?
        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    private static SSLEngineResult.HandshakeStatus unwrap(ByteBuffer cipherIn, ByteBuffer plainIn,
        ReadableByteChannel channel, SSLEngine sslEngine) throws IOException {
        SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

        if (channel.read(cipherIn) < 0) {
            throw new SSLException("Could not read from socket channel");
        }
        cipherIn.flip();

        SSLEngineResult.Status status;
        do {
            SSLEngineResult unwrapResult = sslEngine.unwrap(cipherIn, plainIn);
            status = unwrapResult.getStatus();
            switch (status) {
            case OK:
                plainIn.clear();
                handshakeStatus = runDelegatedTasks(sslEngine);
                break;
            case BUFFER_OVERFLOW:
                throw new SSLException("Buffer overflow during handshake");
            case BUFFER_UNDERFLOW:
                cipherIn.compact();
                int read = NioHelper.read(channel, cipherIn);
                if(read <= 0) {
                    retryRead(channel, cipherIn);
                }
                cipherIn.flip();
                break;
            case CLOSED:
                sslEngine.closeInbound();
                break;
            default:
                throw new SSLException("Unexpected status from " + unwrapResult);
            }
        }
        while (cipherIn.hasRemaining());

        cipherIn.compact();
        return handshakeStatus;
    }

    private static int retryRead(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        int attempt = 0;
        int read = 0;
        while(attempt < 3) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            read = NioHelper.read(channel, buffer);
            if(read > 0) {
                break;
            }
            attempt++;
        }
        return read;
    }

    private static SSLEngineResult.HandshakeStatus wrap(ByteBuffer plainOut, ByteBuffer cipherOut,
        WritableByteChannel channel, SSLEngine sslEngine) throws IOException {
        SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        SSLEngineResult.Status status = sslEngine.wrap(plainOut, cipherOut).getStatus();
        switch (status) {
        case OK:
            handshakeStatus = runDelegatedTasks(sslEngine);
            cipherOut.flip();
            while (cipherOut.hasRemaining()) {
                channel.write(cipherOut);
            }
            cipherOut.clear();
            break;
        case BUFFER_OVERFLOW:
            throw new SSLException("Buffer overflow during handshake");
        default:
            throw new SSLException("Unexpected status " + status);
        }
        return handshakeStatus;
    }

    static int bufferCopy(ByteBuffer from, ByteBuffer to) {
        int maxTransfer = Math.min(to.remaining(), from.remaining());

        ByteBuffer temporaryBuffer = from.duplicate();
        temporaryBuffer.limit(temporaryBuffer.position() + maxTransfer);
        to.put(temporaryBuffer);

        from.position(from.position() + maxTransfer);

        return maxTransfer;
    }

    public static void write(WritableByteChannel socketChannel, SSLEngine engine, ByteBuffer plainOut, ByteBuffer cypherOut) throws IOException {
        while (plainOut.hasRemaining()) {
            cypherOut.clear();
            SSLEngineResult result = engine.wrap(plainOut, cypherOut);
            switch (result.getStatus()) {
            case OK:
                cypherOut.flip();
                while (cypherOut.hasRemaining()) {
                    socketChannel.write(cypherOut);
                }
                break;
            case BUFFER_OVERFLOW:
                throw new SSLException("Buffer overflow occured after a wrap.");
            case BUFFER_UNDERFLOW:
                throw new SSLException("Buffer underflow occured after a wrap.");
            case CLOSED:
                throw new SSLException("Buffer closed");
            default:
                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }
    }

    public static void close(WritableByteChannel channel, SSLEngine engine) throws IOException {
        ByteBuffer plainOut = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer cipherOut = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        // won't be sending any more data
        engine.closeOutbound();

        while (!engine.isOutboundDone()) {
            engine.wrap(plainOut, cipherOut);
            cipherOut.flip();
            while (cipherOut.hasRemaining()) {
                int num = channel.write(cipherOut);
                if (num == -1) {
                    // the channel has been closed
                    break;
                }
            }
            cipherOut.clear();
        }
    }
}
