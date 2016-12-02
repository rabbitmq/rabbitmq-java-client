package com.rabbitmq.client;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Exception thrown when a channel times out on a continuation during a RPC call.
 * @since 4.1.0
 */
public class ChannelContinuationTimeoutException extends IOException {

    /**
     * The channel that performed the call.
     */
    private final Object channel;

    /**
     * The request method that timed out.
     */
    private final Method method;

    public ChannelContinuationTimeoutException(TimeoutException cause, Object channel, Method method) {
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
