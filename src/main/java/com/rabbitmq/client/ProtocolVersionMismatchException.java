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

package com.rabbitmq.client;

import com.rabbitmq.client.impl.Version;

import java.net.ProtocolException;

/**
 * Thrown to indicate that the server does not support the wire protocol version
 * we requested immediately after opening the TCP socket.
 */
public class ProtocolVersionMismatchException extends ProtocolException
{
    /** Default serialVersionUID for serializability without version checking. */
    private static final long serialVersionUID = 1L;
    private final Version clientVersion;
    private final Version serverVersion;

    public ProtocolVersionMismatchException(Version clientVersion,
                                            Version serverVersion) {
        super("Protocol version mismatch: expected "
              + clientVersion + ", got " + serverVersion);

        this.clientVersion = clientVersion;
        this.serverVersion = serverVersion;
    }

    /** The client's AMQP specification version. */
    public Version getClientVersion()
    {
        return clientVersion;
    }

    /** The server's AMQP specification version. */
    public Version getServerVersion()
    {
        return serverVersion;
    }

    /** The client's AMQP specification major version. */
    public int getClientMajor()
    {
        return clientVersion.getMajor();
    }

    /** The client's AMQP specification minor version. */
    public int getClientMinor()
    {
        return clientVersion.getMinor();
    }

    /** The server's AMQP specification major version. */
    public int getServerMajor()
    {
        return serverVersion.getMajor();
    }

    /** The server's AMQP specification minor version. */
    public int getServerMinor()
    {
        return serverVersion.getMinor();
    }
}
