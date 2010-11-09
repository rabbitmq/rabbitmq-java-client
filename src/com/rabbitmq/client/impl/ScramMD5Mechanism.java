package com.rabbitmq.client.impl;

import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.ConnectionFactory;

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

public class ScramMD5Mechanism implements AuthMechanism {
    public LongString handleChallenge(LongString challengeStr,
                                      ConnectionFactory factory) {
        if (challengeStr == null) {
            return LongStringHelper.asLongString(factory.getUsername());
        } else {
            try {
                byte[] challenge = challengeStr.getBytes();
                byte[] salt1 = Arrays.copyOfRange(challenge, 0, 4);
                byte[] salt2 = Arrays.copyOfRange(challenge, 4, 8);

                byte[] pw = factory.getPassword().getBytes("utf-8");
                byte[] d = digest(salt2, digest(salt1, pw));

                return LongStringHelper.asLongString(d);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
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
}
