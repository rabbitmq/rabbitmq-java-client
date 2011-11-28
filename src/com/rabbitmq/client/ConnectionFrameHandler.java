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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.

package com.rabbitmq.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;

/**
 * A {@link ConnectionFrameHandler} object is injected into
 * {@link ConnectionBuilder} to create a {@link FrameHandler}
 * for the {@link Connection}.
 * <br/>
 * The {@link #createFrameHandler createFrameHandler} method may be overridden
 * if complete control over the source of frames is required.
 */
abstract class ConnectionFrameHandler {

    /**
     * Create a {@link FrameHandler} from a {@link ConnectionBuilder}
     * and a {@link ConnectionHelper} object.
     * <p/>
     * This is the default abstract implementation which connects to a socket
     * using the builder information.
     * @param builder being used to build the connection
     * @param helper used to set socket options
     * @return FrameHandler for receiving frames
     * @throws IOException if FrameHandler cannot be created
     */
    FrameHandler createFrameHandler(ConnectionBuilder builder, ConnectionHelper helper)
        throws IOException
    {
        Socket socket = null;
        try {
            socket = builder.getSocketFactory().createSocket();
            helper.configureSocket(socket);
            socket.connect(new InetSocketAddress( builder.getHost()
                                                , builder.getPort())
                                                , builder.getConnectionTimeout());
            return new SocketFrameHandler(socket);
        } catch (IOException ioe) {
            if (socket != null) try {socket.close();} catch (Exception e) {};
            throw ioe;
        }
    }
}
