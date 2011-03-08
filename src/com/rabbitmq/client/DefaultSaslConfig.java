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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of SaslConfig that uses the standard Java
 * algorithm for selecting a sasl client.
 * @see com.rabbitmq.client.ConnectionFactory
 */
public class DefaultSaslConfig implements SaslConfig {
    public static final String[] DEFAULT_PREFERRED_MECHANISMS = new String[]{"PLAIN"};

    private final ConnectionFactory factory;
    private final List<String> mechanisms;
    private final CallbackHandler callbackHandler;

    /**
     * Create a DefaultSaslConfig which only wants to use PLAIN.
     *
     * @param factory - the ConnectionFactory to use to obtain username, password and host
     */
    public DefaultSaslConfig(ConnectionFactory factory) {
        this(factory, DEFAULT_PREFERRED_MECHANISMS);
    }

    /**
     * Create a DefaultSaslConfig with a list of mechanisms to use.
     *
     * @param factory - the ConnectionFactory to use to obtain username, password and host
     * @param mechanisms - a list of SASL mechanisms to use (in descending order of preference)
     */
    public DefaultSaslConfig(ConnectionFactory factory, String[] mechanisms) {
        this.factory = factory;
        callbackHandler = new UsernamePasswordCallbackHandler(factory);
        this.mechanisms = Arrays.asList(mechanisms);
    }

    public SaslClient getSaslClient(String[] serverMechanisms) throws SaslException {
        Set<String> server = new HashSet<String>(Arrays.asList(serverMechanisms));

        for (String mechanism: mechanisms) {
            if (server.contains(mechanism)) {
                SaslClient saslClient = Sasl.createSaslClient(new String[]{mechanism},
                         null, "AMQP", factory.getHost(), null, callbackHandler);
                if (saslClient != null) return saslClient;
            }
        }
        return null;
    }
}
