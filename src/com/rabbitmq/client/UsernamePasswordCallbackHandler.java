package com.rabbitmq.client;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;

public class UsernamePasswordCallbackHandler implements CallbackHandler {
    private ConnectionFactory factory;
    public UsernamePasswordCallbackHandler(ConnectionFactory factory) {
        this.factory = factory;
    }

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback: callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nc = (NameCallback)callback;
                nc.setName(factory.getUsername());

            } else if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback)callback;
                pc.setPassword(factory.getPassword().toCharArray());

            } else {
                throw new UnsupportedCallbackException
                        (callback, "Unrecognized Callback");
            }
        }
    }
}
