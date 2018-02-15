package com.rabbitmq.client.impl;

/**
 * Provider interface for establishing credentials for connecting to the broker. Especially useful
 * for situations where credentials might change before a recovery takes place or where it is 
 * convenient to plug in an outside custom implementation.
 *
 * @since 4.5.0
 */
public interface CredentialsProvider {

    String getUsername();

    String getPassword();

}