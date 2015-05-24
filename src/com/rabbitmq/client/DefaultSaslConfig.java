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
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import com.rabbitmq.client.impl.ExternalMechanism;
import com.rabbitmq.client.impl.PlainMechanism;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Default SASL configuration. Uses one of our built-in mechanisms.
 */
public class DefaultSaslConfig implements SaslConfig {
    private final String mechanism;

    public static final DefaultSaslConfig PLAIN = new DefaultSaslConfig("PLAIN");
    public static final DefaultSaslConfig EXTERNAL = new DefaultSaslConfig("EXTERNAL");

    /**
     * Create a DefaultSaslConfig with an explicit mechanism to use.
     *
     * @param mechanism - a SASL mechanism to use
     */
    private DefaultSaslConfig(String mechanism) {
        this.mechanism = mechanism;
    }

    public SaslMechanism getSaslMechanism(String[] serverMechanisms) {
        Set<String> server = new HashSet<String>(Arrays.asList(serverMechanisms));

        if (server.contains(mechanism)) {
            if (mechanism.equals("PLAIN")) {
                return new PlainMechanism();
            }
            else if (mechanism.equals("EXTERNAL")) {
                return new ExternalMechanism();
            }
        }
        return null;
    }
}
