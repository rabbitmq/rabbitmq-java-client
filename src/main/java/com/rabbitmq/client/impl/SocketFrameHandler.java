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

import com.rabbitmq.client.AMQP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A socket-based frame handler.
 */

public class SocketFrameHandler implements FrameHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketFrameHandler.class);

    /** The underlying socket */
    private final Socket _socket;

    /**
     * Optional {@link ExecutorService} for final flush.
     */
    private final ExecutorService _shutdownExecutor;

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
        this(socket, null);
    }

    /**
     * @param socket the socket to use
     */
    public SocketFrameHandler(Socket socket, ExecutorService shutdownExecutor) throws IOException {
        _socket = socket;
        _shutdownExecutor = shutdownExecutor;

        _inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        _outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    @Override
    public InetAddress getAddress() {
        return _socket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return _socket.getLocalAddress();
    }

    // For testing only
    public DataInputStream getInputStream() {
        return _inputStream;
    }

    @Override
    public int getPort() {
        return _socket.getPort();
    }

    @Override
    public int getLocalPort() {
        return _socket.getLocalPort();
    }

    @Override
    public void setTimeout(int timeoutMs)
        throws SocketException
    {
        _socket.setSoTimeout(timeoutMs);
    }

    @Override
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
            try {
                _outputStream.flush();
            } catch (SSLHandshakeException e) {
                LOGGER.error("TLS connection failed: {}", e.getMessage());
                throw e;
            }
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
            try {
                _outputStream.flush();
            } catch (SSLHandshakeException e) {
                LOGGER.error("TLS connection failed: {}", e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public void sendHeader() throws IOException {
        sendHeader(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR, AMQP.PROTOCOL.REVISION);
        if (this._socket instanceof SSLSocket) {
            TlsUtils.logPeerCertificateInfo(((SSLSocket) this._socket).getSession());
        }
    }

    @Override
    public void initialize(AMQConnection connection) {
        connection.startMainLoop();
    }

    @Override
    public Frame readFrame() throws IOException {
        synchronized (_inputStream) {
            return Frame.readFrom(_inputStream);
        }
    }

    @Override
    public void writeFrame(Frame frame) throws IOException {
        synchronized (_outputStream) {
            frame.writeTo(_outputStream);
        }
    }

    @Override
    public void flush() throws IOException {
        _outputStream.flush();
    }

    @Override
    public void close() {
        try { _socket.setSoLinger(true, SOCKET_CLOSING_TIMEOUT); } catch (Exception _e) {}
        // async flush if possible
        // see https://github.com/rabbitmq/rabbitmq-java-client/issues/194
        Callable<Void> flushCallable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                flush();
                return null;
            }
        };
        Future<Void> flushTask = null;
        try {
            if(this._shutdownExecutor == null) {
                flushCallable.call();
            } else {
                flushTask = this._shutdownExecutor.submit(flushCallable);
                flushTask.get(SOCKET_CLOSING_TIMEOUT, TimeUnit.SECONDS);
            }
        } catch(Exception e) {
            if(flushTask != null) {
                flushTask.cancel(true);
            }
        }
        try { _socket.close();                                   } catch (Exception _e) {}
    }
}
