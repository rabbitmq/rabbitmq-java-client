package com.rabbitmq.client.impl;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.UsernamePasswordCallbackHandler;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
    Provides equivalent security to PLAIN but demos use of Connection.Secure(Ok)
    START-OK: Username
    SECURE: "Please tell me your password"
    SECURE-OK: Password
*/

public class CRDemoSaslClient implements SaslClient {
    private static final String NAME = "RABBIT-CR-DEMO";

    private CallbackHandler handler;
    private int round = 0;

    public CRDemoSaslClient(CallbackHandler handler) {
        this.handler = handler;
    }

    public String getMechanismName() {
        return NAME;
    }

    public boolean hasInitialResponse() {
        return true;
    }

    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
        byte[] resp;
        try {
            if (round == 0) {
                NameCallback nc = new NameCallback("Name:");
                handler.handle(new Callback[]{nc});
                resp = nc.getName().getBytes("utf-8");
            } else {
                PasswordCallback pc = new PasswordCallback("Password:", false);
                handler.handle(new Callback[]{pc});
                resp = new String(pc.getPassword()).getBytes("utf-8");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedCallbackException e) {
            throw new SaslException("Bad callback", e);
        } catch (IOException e) {
            throw new SaslException("IO Exception", e);
        }

        round++;
        return resp;
    }

    public boolean isComplete() {
        return round == 2;
    }

    public byte[] unwrap(byte[] bytes, int i, int i1) throws SaslException {
        throw new UnsupportedOperationException();
    }

    public byte[] wrap(byte[] bytes, int i, int i1) throws SaslException {
        throw new UnsupportedOperationException();
    }

    public Object getNegotiatedProperty(String s) {
        throw new UnsupportedOperationException();
    }

    public void dispose() throws SaslException {
        // NOOP
    }

    public static class CRDemoSaslConfig implements SaslConfig {
        private ConnectionFactory factory;

        public CRDemoSaslConfig(ConnectionFactory factory) {
            this.factory = factory;
        }

        public SaslClient getSaslClient(String[] mechanisms) throws SaslException {
            if (Arrays.asList(mechanisms).contains(NAME)) {
                return new CRDemoSaslClient(new UsernamePasswordCallbackHandler(factory));
            }
            else {
                return null;
            }
        }
    }
}
