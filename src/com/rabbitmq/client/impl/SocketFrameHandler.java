//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import javax.net.SocketFactory;

import com.rabbitmq.client.AMQP;

/**
 * A socket-based frame handler.
 */

public class SocketFrameHandler implements FrameHandler {
    /** Host we connect to */
    public final String _host;

    /** Port number we connect to */
    public final int _port;

    /** The underlying socket */
    public final Socket _socket;

    /** Socket's inputstream - data from the broker */
    public final DataInputStream _inputStream;

    /** Socket's outputstream - data to the broker */
    public final DataOutputStream _outputStream;

    // Note, we use each of these to synchronize on to make sure we don't try to use them
    // twice simultaneously.

    /**
     * Instantiate a SocketFrameHandler.
     * @param factory the socket factory to use to build our Socket - may be SSLSocketFactory etc
     * @param hostName the host name
     * @param portNumber the port number
     * @throws IOException if there is a problem accessing the connection
     */
    public SocketFrameHandler(SocketFactory factory,
                              String hostName,
                              int portNumber)
        throws IOException
    {
        _host = hostName;
        _port = portNumber;
        _socket = factory.createSocket(_host, _port);
        //disable Nagle's algorithm, for more consistently low latency
        _socket.setTcpNoDelay(true);

        _inputStream = new DataInputStream(new BufferedInputStream(_socket.getInputStream()));
        _outputStream = new DataOutputStream(new BufferedOutputStream(_socket.getOutputStream()));
    }

    public String getHost() {
        return _host;
    }

    public int getPort() {
        return _port;
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
     * Write a connection header to the underlying socket, containing
     * the specified version information, kickstarting the AMQP
     * protocol version negotiation process.
     *
     * @param major major protocol version number
     * @param minor minor protocol version number
     * @throws IOException if there is a problem accessing the connection
     * @see com.rabbitmq.client.impl.FrameHandler#sendHeader()
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
     * Write a connection header to the underlying socket, containing
     * the protocol version supported by this code, kickstarting the
     * AMQP protocol version negotiation process.
     *
     * @throws IOException if there is a problem accessing the connection
     * @see com.rabbitmq.client.impl.FrameHandler#sendHeader()
     */
    public void sendHeader() throws IOException {
        sendHeader(AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR);
    }

    /**
     * Read a {@link Frame} from the underlying socket.
     * @see FrameHandler#readFrame()
     * @return an incoming Frame, or null if there is none
     * @throws IOException if there is a problem accessing the connection
     */
    public Frame readFrame() throws IOException {
        synchronized (_inputStream) {
            return Frame.readFrom(_inputStream);
        }
    }

    /**
     * Write a {@link Frame} to the underlying socket.
     * @param frame an incoming Frame, or null if there is none
     * @throws IOException if there is a problem accessing the connection
     * @see FrameHandler#writeFrame(Frame frame)
     */
    public void writeFrame(Frame frame) throws IOException {
        synchronized (_outputStream) {
            frame.writeTo(_outputStream);
            _outputStream.flush();
        }
    }

    public void close() {
        try {
            _socket.close();
        } catch (IOException ioe) {
            // Ignore.
        }
    }
}
