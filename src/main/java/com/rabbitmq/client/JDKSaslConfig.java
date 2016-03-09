//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import com.rabbitmq.client.impl.LongStringHelper;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of SaslConfig that uses the JDK SASL implementation. This is
 * not the default since it does not work on Java 1.4, Android or IBM's JDK.
 * @see com.rabbitmq.client.ConnectionFactory
 */
public class JDKSaslConfig implements SaslConfig {
    private static final String[] DEFAULT_PREFERRED_MECHANISMS = new String[]{"PLAIN"};

    private final ConnectionFactory factory;
    private final List<String> mechanisms;
    private final CallbackHandler callbackHandler;

    /**
     * Create a JDKSaslConfig which only wants to use PLAIN.
     *
     * @param factory - the ConnectionFactory to use to obtain username, password and host
     */
    public JDKSaslConfig(ConnectionFactory factory) {
        this(factory, DEFAULT_PREFERRED_MECHANISMS);
    }

    /**
     * Create a JDKSaslConfig with a list of mechanisms to use.
     *
     * @param factory - the ConnectionFactory to use to obtain username, password and host
     * @param mechanisms - a list of SASL mechanisms to use (in descending order of preference)
     */
    public JDKSaslConfig(ConnectionFactory factory, String[] mechanisms) {
        this.factory = factory;
        callbackHandler = new UsernamePasswordCallbackHandler(factory);
        this.mechanisms = Arrays.asList(mechanisms);
    }

    public SaslMechanism getSaslMechanism(String[] serverMechanisms) {
        Set<String> server = new HashSet<String>(Arrays.asList(serverMechanisms));

        for (String mechanism: mechanisms) {
            if (server.contains(mechanism)) {
                try {
                    SaslClient saslClient = Sasl.createSaslClient(new String[]{mechanism},
                             null, "AMQP", factory.getHost(), null, callbackHandler);
                    if (saslClient != null) return new JDKSaslMechanism(saslClient);
                } catch (SaslException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

    private class JDKSaslMechanism implements SaslMechanism {
        private final SaslClient client;

        public JDKSaslMechanism(SaslClient client) {
            this.client = client;
        }

        public String getName() {
            return client.getMechanismName();
        }

        public LongString handleChallenge(LongString challenge, String username, String password) {
            try {
                return LongStringHelper.asLongString(client.evaluateChallenge(challenge.getBytes()));
            } catch (SaslException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class UsernamePasswordCallbackHandler implements CallbackHandler {
        private final ConnectionFactory factory;
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

}
