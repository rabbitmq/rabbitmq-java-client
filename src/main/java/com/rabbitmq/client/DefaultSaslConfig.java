// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

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

    @Override
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
