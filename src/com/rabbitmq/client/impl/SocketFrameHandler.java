//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

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
        try { flush();                                           } catch (Exception _e) {}
        try { _socket.close();                                   } catch (Exception _e) {}
    }
}
