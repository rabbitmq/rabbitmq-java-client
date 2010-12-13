package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.PossibleAuthenticationFailureException;
import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.test.BrokerTestCase;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.util.Arrays;

public class SaslMechanisms extends BrokerTestCase {
    private String[] mechanisms;

    public class Client implements SaslClient {
        public Client(String name, byte[][] responses) {
            this.name = name;
            this.responses = responses;
        }

        private String name;
        private byte[][] responses;
        private int counter;

        public String getMechanismName() {
            return name;
        }

        public boolean hasInitialResponse() {
            return true;
        }

        public byte[] evaluateChallenge(byte[] bytes) throws SaslException {
            counter ++;
            return responses[counter-1];
        }

        public boolean isComplete() {
            return counter >= responses.length;
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
        }
    }

    public class Config implements SaslConfig {
        private String name;
        private byte[][] responses;

        public Config(String name, byte[][] responses) {
            this.name = name;
            this.responses = responses;
        }

        public SaslClient getSaslClient(String[] mechanisms) throws SaslException {
            SaslMechanisms.this.mechanisms = mechanisms;
            return new Client(name, responses);
        }
    }

    // TODO test gibberish examples. ATM the server is not very robust.

    public void testPlainLogin() throws IOException {
        loginOk("PLAIN", new byte[][] {"\0guest\0guest".getBytes()} );
        loginBad("PLAIN", new byte[][] {"\0guest\0wrong".getBytes()} );
    }

    public void testAMQPlainLogin() throws IOException {
        // guest / guest
        loginOk("AMQPLAIN", new byte[][] {{5,76,79,71,73,78,83,0,0,0,5,103,117,101,115,116,8,80,65,83,83,87,79,82,68,83,0,0,0,5,103,117,101,115,116}} );
        // guest / wrong
        loginBad("AMQPLAIN", new byte[][] {{5,76,79,71,73,78,83,0,0,0,5,103,117,101,115,116,8,80,65,83,83,87,79,82,68,83,0,0,0,5,119,114,111,110,103}} );
    }

    public void testCRLogin() throws IOException {
        // Make sure mechanisms is populated
        loginOk("PLAIN", new byte[][] {"\0guest\0guest".getBytes()} );

        // We might be running this standalone
        if (Arrays.asList(mechanisms).contains("RABBIT-CR-DEMO")) {
            loginOk("RABBIT-CR-DEMO", new byte[][] {"guest".getBytes(), "My password is guest".getBytes()} );
            loginBad("RABBIT-CR-DEMO", new byte[][] {"guest".getBytes(), "My password is wrong".getBytes()} );
        }
    }

    private void loginOk(String name, byte[][] responses) throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setSaslConfig(new Config(name, responses));
        Connection connection = factory.newConnection();
        connection.close();
    }

    private void loginBad(String name, byte[][] responses) throws IOException {
        try {
            loginOk(name, responses);
            fail("Login succeeded!");
        } catch (PossibleAuthenticationFailureException e) {
            // Ok
        }
    }
}
