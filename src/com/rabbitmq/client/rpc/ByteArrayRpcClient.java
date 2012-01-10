package com.rabbitmq.client.rpc;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * An implementation of {@link RpcClient} which simply passes a simple byte array parameter to the
 * server, and expects a byte array response.
 */
public class ByteArrayRpcClient implements RpcClient<byte[], byte[]> {

    private final RpcCaller rpcCaller;
    private final String exchange;
    private final String routingKey;

    /**
     * Construct an {@link RpcClient} which calls a fixed RPC Server (identified by
     * <code>exchange</code> and <code>routingKey</code>) using the supplied {@link RpcCaller}.
     *
     * @param exchange to supply to caller
     * @param routingKey to supply to caller
     * @param rpcCaller to use to make remote call
     */
    public ByteArrayRpcClient(String exchange, String routingKey, RpcCaller rpcCaller) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.rpcCaller = rpcCaller;
    }

    public byte[] call(byte[] request) throws IOException, TimeoutException,
            ShutdownSignalException {
        return this.rpcCaller.call(this.exchange, this.routingKey, request);
    }

}
