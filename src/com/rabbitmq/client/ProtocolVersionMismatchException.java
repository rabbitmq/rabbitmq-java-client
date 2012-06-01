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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
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
    private Version clientVersion;
    private Version serverVersion;

    /**
     * @param clientVersion version requested by client
     * @param serverVersion version offered by server
     */
    public ProtocolVersionMismatchException(Version clientVersion,
                                            Version serverVersion) {
        super("Protocol version mismatch: expected "
              + clientVersion + ", got " + serverVersion);

        this.clientVersion = clientVersion;
        this.serverVersion = serverVersion;
    }

    /** @return the client's AMQP specification version. */
    public Version getClientVersion()
    {
        return clientVersion;
    }

    /** @return the server's AMQP specification version. */
    public Version getServerVersion()
    {
        return serverVersion;
    }

    /** @return the client's AMQP specification major version. */
    public int getClientMajor()
    {
        return clientVersion.getMajor();
    }

    /** @return the client's AMQP specification minor version. */
    public int getClientMinor()
    {
        return clientVersion.getMinor();
    }

    /** @return the server's AMQP specification major version. */
    public int getServerMajor()
    {
        return serverVersion.getMajor();
    }

    /** @return the server's AMQP specification minor version. */
    public int getServerMinor()
    {
        return serverVersion.getMinor();
    }
}
