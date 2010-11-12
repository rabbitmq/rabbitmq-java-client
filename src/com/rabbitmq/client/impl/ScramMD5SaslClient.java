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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
    START-OK: Username
    SECURE: {Salt1, Salt2} (where Salt1 is the salt from the db and
    Salt2 differs every time)
    SECURE-OK: md5(Salt2 ++ md5(Salt1 ++ Password))

    The second salt is there to defend against replay attacks. The
    first is needed since the passwords are salted in the db.

    This is only somewhat improved security over PLAIN (if you can
    break MD5 you can still replay attack) but it's better than nothing
    and mostly there to prove the use of SECURE / SECURE-OK frames.
*/

public class ScramMD5SaslClient implements SaslClient {
    private static final String NAME = "RABBIT-SCRAM-MD5";

    private CallbackHandler handler;
    private int round = 0;

    public ScramMD5SaslClient(CallbackHandler handler) {
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
                byte[] salt1 = Arrays.copyOfRange(challenge, 0, 4);
                byte[] salt2 = Arrays.copyOfRange(challenge, 4, 8);
                PasswordCallback pc = new PasswordCallback("Password:", false);
                handler.handle(new Callback[]{pc});
                byte[] pw = new String(pc.getPassword()).getBytes("utf-8");
                resp = digest(salt2, digest(salt1, pw));
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

    private static byte[] digest(byte[] arr1, byte[] arr2) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return digest.digest(concat(arr1, arr2));

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static class ScramMD5SaslConfig implements SaslConfig {
        private ConnectionFactory factory;

        public ScramMD5SaslConfig(ConnectionFactory factory) {
            this.factory = factory;
        }

        public SaslClient getSaslClient(String[] mechanisms) throws SaslException {
            if (Arrays.asList(mechanisms).contains(NAME)) {
                return new ScramMD5SaslClient(new UsernamePasswordCallbackHandler(factory));
            }
            else {
                return null;
            }
        }
    }
}
