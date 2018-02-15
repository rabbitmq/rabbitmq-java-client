package com.rabbitmq.client.impl;

/**
 * Default implementation of a CredentialsProvider which simply holds a static
 * username and password.
 *
 * @since 4.5.0
 */
public class DefaultCredentialsProvider implements CredentialsProvider {

    private final String username;
    private final String password;

    public DefaultCredentialsProvider(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }
    
}
