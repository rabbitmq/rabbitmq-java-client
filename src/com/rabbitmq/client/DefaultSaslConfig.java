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

    private ConnectionFactory factory;
    private String authorizationId;
    private String[] preferredMechanisms = DEFAULT_PREFERRED_MECHANISMS;
    private CallbackHandler callbackHandler;

    public DefaultSaslConfig(ConnectionFactory factory) {
        this.factory = factory;
        callbackHandler = new UsernamePasswordCallbackHandler(factory);
    }

    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }

    /**
     * Set a list of SASL mechanisms to use (in descending order of preference)
     * @param preferredMechanisms
     */
    public void setPreferredMechanisms(String[] preferredMechanisms) {
        this.preferredMechanisms = preferredMechanisms;
    }

    public void setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    public SaslClient getSaslClient(String[] serverMechanisms) throws SaslException {
        List<String> server = Arrays.asList(serverMechanisms);
        List<String> client = Arrays.asList(preferredMechanisms);
        client.retainAll(server);

        for (String mechanism: client) {
            SaslClient saslClient = Sasl.createSaslClient(new String[]{mechanism},
                     authorizationId, "AMQP", factory.getHost(), null, callbackHandler);
            if (saslClient != null) return saslClient;
        }
        return null;
    }
}
