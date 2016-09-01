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

package com.rabbitmq.client.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;

import com.rabbitmq.client.AMQP;

/**
 * A socket-based frame handler.
 */

public class SocketFrameHandler implements FrameHandler {
    /** The underlying socket */
    private final Socket _socket;

    /** Socket's inputstream - data from the broker - synchronized on */
    private final DataInputStream _inputStream;

    /** Socket's outputstream - data to the broker - synchronized on */
    private final DataOutputStream _outputStream;

    /** Time to linger before closing the socket forcefully. */
    public static final int SOCKET_CLOSING_TIMEOUT = 1;

    /**
     * @param socket the socket to use
     */
    public SocketFrameHandler(Socket socket) throws IOException {
        _socket = socket;

        _inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        _outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public InetAddress getAddress() {
        return _socket.getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return _socket.getLocalAddress();
    }

    // For testing only
    public DataInputStream getInputStream() {
        return _inputStream;
    }

    public int getPort() {
        return _socket.getPort();
    }

    public int getLocalPort() {
        return _socket.getLocalPort();
    }

    public void setTimeout(int timeoutMs)
        throws SocketException
    {
        _socket.setSoTimeout(timeoutMs);
    }

    public int getTimeout()
        throws SocketException
    {
        return _socket.getSoTimeout();
    }

    /**
     * Write a 0-8-style connection header to the underlying socket,
     * containing the specified version information, kickstarting the
     * AMQP protocol version negotiation process.
     *
     * @param major major protocol version number
     * @param minor minor protocol version number
     * @throws IOException if there is a problem accessing the connection
     * @see #sendHeader()
     */
    public void sendHeader(int major, int minor) throws IOException {
        synchronized (_outputStream) {
            _outputStream.write("AMQP".getBytes("US-ASCII"));
            _outputStream.write(1);
            _outputStream.write(1);
            _outputStream.write(major);
            _outputStream.write(minor);
            _outputStream.flush();
        }
    }

   /**
     * Write a 0-9-1-style connection header to the underlying socket,
     * containing the specified version information, kickstarting the
     * AMQP protocol version negotiation process.
     *
     * @param major major protocol version number
     * @param minor minor protocol version number
     * @param revision protocol revision number
     * @throws IOException if there is a problem accessing the connection
     * @see #sendHeader()
     */
  public void sendHeader(int major, int minor, int revision) throws IOException {
        synchronized (_outputStream) {
            _outputStream.write("AMQP".getBytes("US-ASCII"));
            _outputStream.write(0);
            _outputStream.write(major);
            _outputStream.write(minor);
            _outputStream.write(revision);
            _outputStream.flush();
        }
    }

    public void sendHeader() throws IOException {
        sendHeader(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR, AMQP.PROTOCOL.REVISION);
    }

    public Frame readFrame() throws IOException {
        synchronized (_inputStream) {
            return Frame.readFrom(_inputStream);
        }
    }

    public void writeFrame(Frame frame) throws IOException {
        synchronized (_outputStream) {
            frame.writeTo(_outputStream);
        }
    }

    public void flush() throws IOException {
        _outputStream.flush();
    }

    @SuppressWarnings("unused")
    public void close() {
        try { _socket.setSoLinger(true, SOCKET_CLOSING_TIMEOUT); } catch (Exception _e) {}
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Void> flushTask = executorService.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    flush();
                    return null;
                }
            });
            flushTask.get(SOCKET_CLOSING_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception _e) {

        } finally {
            executorService.shutdownNow();
        }
        try { _socket.close();                                   } catch (Exception _e) {}
    }
}
