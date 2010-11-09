package com.rabbitmq.client;

import com.rabbitmq.client.impl.Version;

import java.io.IOException;
import java.net.ProtocolException;

/**
 * Thrown to indicate that the server does not support the wire protocol version
 * we requested immediately after opening the TCP socket.
 */
public class ProtocolVersionMismatchException extends ProtocolException
{
    private Version clientVersion;
    private Version serverVersion;

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
