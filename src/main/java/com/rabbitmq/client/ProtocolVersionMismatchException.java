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
