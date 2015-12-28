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

import java.io.IOException;

/**
 * Our own view of a SASL authentication mechanism, introduced to remove a
 * dependency on javax.security.sasl.
 */
public interface SaslMechanism {
    /**
     * The name of this mechanism (e.g. PLAIN)
     * @return the name
     */
    String getName();

    /**
     * Handle one round of challenge-response
     * @param challenge the challenge this round, or null on first round.
     * @param username name of user
     * @param password for username
     * @return response
     * @throws IOException
     */
    LongString handleChallenge(LongString challenge, String username, String password);
}
