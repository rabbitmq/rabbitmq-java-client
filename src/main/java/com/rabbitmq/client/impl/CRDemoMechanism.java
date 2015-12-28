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

    public String getName() {
        return NAME;
    }

    public LongString handleChallenge(LongString challenge, String username, String password) {
        round++;
        if (round == 1) {
            return LongStringHelper.asLongString(username);
        } else {
            return LongStringHelper.asLongString("My password is " + password);
        }
    }

    public static class CRDemoSaslConfig implements SaslConfig {
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
