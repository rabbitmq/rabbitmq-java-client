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
     * Typed as <code>Object</code> as the underlying
     * object that performs the call might
     * not be an implementation of {@link Channel}.
     */
    private final Object channel;

    /**
     * The number of the channel that performed the call.
     */
    private final int channelNumber;

    /**
     * The request method that timed out.
     */
    private final Method method;

    public ChannelContinuationTimeoutException(TimeoutException cause, Object channel, int channelNumber, Method method) {
        super(
            "Continuation call for method " + method + " on channel " + channel + " (#" + channelNumber + ") timed out",
            cause
        );
        this.channel = channel;
        this.channelNumber = channelNumber;
        this.method = method;
    }

    /**
     *
     * @return request method that timed out
     */
    public Method getMethod() {
        return method;
    }

    /**
     * channel that performed the call
     * @return
     */
    public Object getChannel() {
        return channel;
    }

    /**
     *
     * @return number of the channel that performed the call
     */
    public int getChannelNumber() {
        return channelNumber;
    }
}
