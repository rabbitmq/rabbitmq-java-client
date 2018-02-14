package com.rabbitmq.client.impl;

/**
 * Default implementation of a CredentialsProvider which simply holds a static
 * username and password.
 */
public class DefaultCredentialsProvider extends AbstractCredentialsProvider {

    protected String username;
    protected String password;
    
    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public void setUsername(String username) {
        this.username = username;
    }
    
    @Override
    public void setPassword(String password) {
        this.password = password;
    }

}
