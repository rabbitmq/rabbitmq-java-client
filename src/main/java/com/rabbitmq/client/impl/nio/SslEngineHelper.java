// Copyright (c) 2007-2022 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.impl.nio;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_TASK;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;

/**
 *
 */
public class SslEngineHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SslEngineHelper.class);

    public static boolean doHandshake(WritableByteChannel writeChannel, ReadableByteChannel readChannel, SSLEngine engine) throws IOException {

        ByteBuffer plainOut = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer plainIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer cipherOut = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer cipherIn = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        LOGGER.debug("Starting TLS handshake");

        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        LOGGER.debug("Initial handshake status is {}", handshakeStatus);
        while (handshakeStatus != FINISHED && handshakeStatus != NOT_HANDSHAKING) {
            LOGGER.debug("Handshake status is {}", handshakeStatus);
            switch (handshakeStatus) {
            case NEED_TASK:
                LOGGER.debug("Running tasks");
                handshakeStatus = runDelegatedTasks(engine);
                break;
            case NEED_UNWRAP:
                LOGGER.debug("Unwrapping...");
                handshakeStatus = unwrap(cipherIn, plainIn, readChannel, engine);
                break;
            case NEED_WRAP:
                LOGGER.debug("Wrapping...");
                handshakeStatus = wrap(plainOut, cipherOut, writeChannel, engine);
                break;
            case FINISHED:
                break;
            case NOT_HANDSHAKING:
                break;
            default:
                throw new SSLException("Unexpected handshake status " + handshakeStatus);
            }
        }


        LOGGER.debug("TLS handshake completed");
        return true;
    }

    private static SSLEngineResult.HandshakeStatus runDelegatedTasks(SSLEngine sslEngine) {
        // FIXME run in executor?
        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            LOGGER.debug("Running delegated task");
            runnable.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    private static SSLEngineResult.HandshakeStatus unwrap(ByteBuffer cipherIn, ByteBuffer plainIn,
        ReadableByteChannel channel, SSLEngine sslEngine) throws IOException {
        SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        LOGGER.debug("Handshake status is {} before unwrapping", handshakeStatus);

        LOGGER.debug("Cipher in position {}", cipherIn.position());
        int read;
        if (cipherIn.position() == 0) {
            LOGGER.debug("Reading from channel");
            read = channel.read(cipherIn);
            LOGGER.debug("Read {} byte(s) from channel", read);
            if (read < 0) {
                throw new SSLException("Could not read from socket channel");
            }
            cipherIn.flip();
        } else {
            LOGGER.debug("Not reading");
        }

        SSLEngineResult.Status status;
        SSLEngineResult unwrapResult;
        do {
            int positionBeforeUnwrapping = cipherIn.position();
            unwrapResult = sslEngine.unwrap(cipherIn, plainIn);
            LOGGER.debug("SSL engine result is {} after unwrapping", unwrapResult);
            status = unwrapResult.getStatus();
            switch (status) {
            case OK:
                plainIn.clear();
                if (unwrapResult.getHandshakeStatus() == NEED_TASK) {
                    handshakeStatus = runDelegatedTasks(sslEngine);
                    int newPosition = positionBeforeUnwrapping + unwrapResult.bytesConsumed();
                    if (newPosition == cipherIn.limit()) {
                        LOGGER.debug("Clearing cipherIn because all bytes have been read and unwrapped");
                        cipherIn.clear();
                    } else {
                        LOGGER.debug("Setting cipherIn position to {} (limit is {})", newPosition, cipherIn.limit());
                        cipherIn.position(positionBeforeUnwrapping + unwrapResult.bytesConsumed());
                    }
                } else {
                    handshakeStatus = unwrapResult.getHandshakeStatus();
                }
                break;
            case BUFFER_OVERFLOW:
                throw new SSLException("Buffer overflow during handshake");
            case BUFFER_UNDERFLOW:
                LOGGER.debug("Buffer underflow");
                cipherIn.compact();
                LOGGER.debug("Reading from channel...");
                read = NioHelper.read(channel, cipherIn);
                if(read <= 0) {
                    retryRead(channel, cipherIn);
                }
                LOGGER.debug("Done reading from channel...");
                cipherIn.flip();
                break;
            case CLOSED:
                sslEngine.closeInbound();
                break;
            default:
                throw new SSLException("Unexpected status from " + unwrapResult);
            }
        }
        while (unwrapResult.getHandshakeStatus() != NEED_WRAP && unwrapResult.getHandshakeStatus() != FINISHED);

        LOGGER.debug("cipherIn position after unwrap {}", cipherIn.position());
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
        LOGGER.debug("Handshake status is {} before wrapping", handshakeStatus);
        SSLEngineResult result = sslEngine.wrap(plainOut, cipherOut);
        LOGGER.debug("SSL engine result is {} after wrapping", result);
        switch (result.getStatus()) {
        case OK:
            cipherOut.flip();
            while (cipherOut.hasRemaining()) {
                int written = channel.write(cipherOut);
                LOGGER.debug("Wrote {} byte(s)", written);
            }
            cipherOut.clear();
            if (result.getHandshakeStatus() == NEED_TASK) {
                handshakeStatus = runDelegatedTasks(sslEngine);
            } else {
                handshakeStatus = result.getHandshakeStatus();
            }

            break;
        case BUFFER_OVERFLOW:
            throw new SSLException("Buffer overflow during handshake");
        default:
            throw new SSLException("Unexpected status " + result.getStatus());
        }
        return handshakeStatus;
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
