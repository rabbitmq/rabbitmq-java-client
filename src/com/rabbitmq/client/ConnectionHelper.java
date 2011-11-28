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
import java.net.Socket;

/**
 * Helper class for the {@link ConnectionBuilder} class.
 * <br/>
 * Overriding methods may call the default behaviour by referring to the super class method.
 */
public abstract class ConnectionHelper {
    /**
     *  A hook to configure sockets used to connect to an AMQP server
     *  before the connection is established.
     *
     *  @param socket the socket to be used for the {@link Connection}
     *  @throws IOException if thrown, the connection fails.
     */
    public void configureSocket(Socket socket) throws IOException
    {
        // disable Nagle's algorithm, for more consistently low latency
        socket.setTcpNoDelay(true);
    }
}
