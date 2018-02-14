package com.rabbitmq.client.impl;

/**
 * Provider interface for establishing credentials for connecting to the broker. Especially useful
 * for situations where credentials might change before a recovery takes place or where it is 
 * convenient to plug in an outside custom implementation.
 * 
 * @see com.rabbitmq.client.impl.AbstractCredentialsProvider
 */
public interface CredentialsProvider extends Cloneable {

    String getUsername();

    String getPassword();

    void setUsername(String username);

    void setPassword(String password);

    Object clone() throws CloneNotSupportedException;
    
}