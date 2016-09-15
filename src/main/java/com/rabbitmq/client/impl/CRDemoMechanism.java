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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.LongString;
import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.SaslMechanism;

import java.util.Arrays;

/**
    Provides equivalent security to PLAIN but demos use of Connection.Secure(Ok)
    START-OK: Username
    SECURE: "Please tell me your password"
    SECURE-OK: Password
*/

public class CRDemoMechanism implements SaslMechanism {
    private static final String NAME = "RABBIT-CR-DEMO";

    private int round = 0;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public LongString handleChallenge(LongString challenge, String username, String password) {
        round++;
        if (round == 1) {
            return LongStringHelper.asLongString(username);
        } else {
            return LongStringHelper.asLongString("My password is " + password);
        }
    }

    public static class CRDemoSaslConfig implements SaslConfig {
        @Override
        public SaslMechanism getSaslMechanism(String[] mechanisms)  {
            if (Arrays.asList(mechanisms).contains(NAME)) {
                return new CRDemoMechanism();
            }
            else {
                return null;
            }
        }
    }
}
