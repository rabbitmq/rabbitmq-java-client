package com.rabbitmq.client.rpc;

import java.io.IOException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Envelope;

/**
 * Abstract class to help create an {@link RpcHandler} with varying degrees of indifference.
 * <p/>
 * The methods on {@link RpcHandler} are given default implementations on calls with fewer and fewer
 * parameters. By implementing only the methods with the least information necessary, an
 * {@link RpcHandler} can be created for use in an {@link RpcProcessor}. If the smallest signature
 * abstract methods are actually called, an {@link UnsupportedOperationException} is thrown.
 * @param <P> parameter type for calls
 * @param <R> return type for calls
 */
public abstract class AbstractRpcHandler<P, R> implements RpcHandler<P, R> {

    public R handleCall(Envelope envelope, BasicProperties requestProperties,
            P parm, BasicProperties replyProperties) throws IOException {
        return handleCall(requestProperties, parm, replyProperties);
    }

    public void handleCast(Envelope envelope,
            BasicProperties requestProperties, P parm) throws IOException {
        handleCast(requestProperties, parm);
    }

    public R handleCall(BasicProperties requestProperties, P parm,
            BasicProperties replyProperties) throws IOException {
        return handleCall(parm, replyProperties);
    }

    public void handleCast(BasicProperties requestProperties, P parm)
            throws IOException {
        handleCast(parm);
    }

    public R handleCall(P parm, BasicProperties replyProperties)
            throws IOException {
        return handleCall(parm);
    }

    public void handleCast(P parm) throws IOException {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    public R handleCall(P parm) throws IOException {
        throw new UnsupportedOperationException("Method not implemented.");
    }
}
