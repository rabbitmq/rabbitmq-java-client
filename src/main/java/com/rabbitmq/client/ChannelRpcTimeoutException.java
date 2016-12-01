package com.rabbitmq.client;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 *
 */
public class ChannelRpcTimeoutException extends IOException {

    private final Object channel;

    private final Method method;

    public ChannelRpcTimeoutException(TimeoutException cause, Object channel, Method method) {
        super(cause);
        this.channel = channel;
        this.method = method;
    }

    public Method getMethod() {
        return method;
    }

    public Object getChannel() {
        return channel;
    }
}
