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

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

/**
 *
 */
public class SocketChannelFrameHandler implements FrameHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandler.class);

    private final SocketChannelFrameHandlerState state;

    public SocketChannelFrameHandler(SocketChannelFrameHandlerState state) {
        this.state = state;
    }

    @Override
    public InetAddress getLocalAddress() {
        return state.getChannel().socket().getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        return state.getChannel().socket().getLocalPort();
    }

    @Override
    public InetAddress getAddress() {
        return state.getChannel().socket().getInetAddress();
    }

    @Override
    public int getPort() {
        return state.getChannel().socket().getPort();
    }

    @Override
    public void setTimeout(int timeoutMs) throws SocketException {
        state.getChannel().socket().setSoTimeout(timeoutMs);
    }

    @Override
    public int getTimeout() throws SocketException {
        return state.getChannel().socket().getSoTimeout();
    }

    @Override
    public void sendHeader() throws IOException {
        state.sendHeader();
    }

    @Override
    public void initialize(AMQConnection connection) {
        state.setConnection(connection);
    }

    @Override
    public Frame readFrame() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeFrame(Frame frame) throws IOException {
        state.write(frame);
    }

    @Override
    public void flush() throws IOException {

    }

    @Override
    public void close() {
        try {
            state.close();
        } catch (IOException e) {
            LOGGER.warn("Error while closing SocketChannel", e);
        }
    }

    public SocketChannelFrameHandlerState getState() {
        return state;
    }
}
