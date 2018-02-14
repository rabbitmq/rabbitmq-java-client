package com.rabbitmq.client.impl;

/**
 * Base class for extending to implement a concrete CredentialsProvider, used
 * when creating connections to the broker or reconnecting during recovery.
 */
public abstract class AbstractCredentialsProvider implements CredentialsProvider {

    /* (non-Javadoc)
     * @see com.rabbitmq.client.impl.CredentialsProvider#getUsername()
     */
    @Override
    public abstract String getUsername();

    /* (non-Javadoc)
     * @see com.rabbitmq.client.impl.CredentialsProvider#getPassword()
     */
    @Override
    public abstract String getPassword();

    /** 
     * Default implementation throws UnsupportedOperationException() but can be overridden for 
     * classes which support setting a static username.
     */
    @Override
    public void setUsername(String username) {
        throw new UnsupportedOperationException();
    }

    /** 
     * Default implementation throws UnsupportedOperationException() but can be overridden for
     * classes which support setting a static password.
     */
    @Override
    public void setPassword(String password) {
        throw new UnsupportedOperationException();
    }

}
