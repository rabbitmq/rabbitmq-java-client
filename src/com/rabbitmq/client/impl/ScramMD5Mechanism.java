package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AuthMechanism;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
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
    public AMQP.Connection.Tune doLogin(AMQChannel channel,
                                        ConnectionFactory factory) throws IOException {
        try {
            LongString resp1 = LongStringHelper.asLongString(factory.getUsername());
            AMQImpl.Connection.StartOk startOk =
                new AMQImpl.Connection.StartOk(factory.getClientProperties(), getName(),
                                               resp1, "en_US");
            AMQP.Connection.Secure secure =
                    (AMQP.Connection.Secure) channel.rpc(startOk).getMethod();
            byte[] challenge = secure.getChallenge().getBytes();
            byte[] salt1 = Arrays.copyOfRange(challenge, 0, 4);
            byte[] salt2 = Arrays.copyOfRange(challenge, 4, 8);

            MessageDigest digest1 = MessageDigest.getInstance("MD5");
            MessageDigest digest2 = MessageDigest.getInstance("MD5");
            byte[] d1 = digest1.digest(concat(salt1, factory.getPassword().getBytes("utf-8")));
            byte[] d2 = digest2.digest(concat(salt2, d1));

            AMQImpl.Connection.SecureOk secureOk =
                new AMQImpl.Connection.SecureOk(LongStringHelper.asLongString(d2));

            return (AMQP.Connection.Tune) channel.rpc(secureOk).getMethod();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (ShutdownSignalException e) {
            throw AMQChannel.wrap(e, "Possibly caused by authentication failure");
        }
    }

    public String getName() {
        return "RABBIT-SCRAM-MD5";
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
