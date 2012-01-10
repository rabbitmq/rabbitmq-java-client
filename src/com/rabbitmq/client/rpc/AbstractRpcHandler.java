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
 *
 * @param <P> parameter type for calls
 * @param <R> return type for calls
 */
public abstract class AbstractRpcHandler<P, R> implements RpcHandler<P, R> {

    public R handleCall(Envelope envelope, BasicProperties requestProperties, P parm,
            BasicProperties replyProperties) throws IOException {
        return handleCall(requestProperties, parm, replyProperties);
    }

    /**
     * Override this to implement {@link RpcHandler#handleCall} ignoring all but parameters
     * <code>requestProperties</code>, <code>parm</code> and <code>replyProperties</code>.
     *
     * @param requestProperties of request message
     * @param parm parameter
     * @param replyProperties for response message
     * @return response
     * @throws IOException if there is an error
     */
    public R handleCall(@SuppressWarnings("unused") BasicProperties requestProperties, P parm,
            BasicProperties replyProperties) throws IOException {
        return handleCall(parm, replyProperties);
    }

    /**
     * Override this to implement {@link RpcHandler#handleCall} ignoring all but parameters
     * <code>parm</code> and <code>replyProperties</code>.
     *
     * @param parm parameter
     * @param replyProperties for response message
     * @return response
     * @throws IOException if there is an error
     */
    public R handleCall(P parm, @SuppressWarnings("unused") BasicProperties replyProperties)
            throws IOException {
        return handleCall(parm);
    }

    /**
     * Override this to implement {@link RpcHandler#handleCall} ignoring all but parameter
     * <code>parm</code>.
     *
     * @param parm parameter
     * @return response
     * @throws IOException if there is an error
     */
    public R handleCall(@SuppressWarnings("unused") P parm) throws IOException {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    public void handleCast(Envelope envelope, BasicProperties requestProperties, P parm)
            throws IOException {
        handleCast(requestProperties, parm);
    }

    /**
     * Override this to implement {@link RpcHandler#handleCast} ignoring all but parameters
     * <code>requestProperties</code> and <code>parm</code>.
     *
     * @param requestProperties of request message
     * @param parm parameter
     * @throws IOException if there is an error
     */
    public void handleCast(@SuppressWarnings("unused") BasicProperties requestProperties, P parm)
            throws IOException {
        handleCast(parm);
    }

    /**
     * Override this to implement {@link RpcHandler#handleCast} ignoring all but parameter
     * <code>parm</code>.
     *
     * @param parm parameter
     * @throws IOException if there is an error
     */
    public void handleCast(@SuppressWarnings("unused") P parm) throws IOException {
        throw new UnsupportedOperationException("Method not implemented.");
    }
}
