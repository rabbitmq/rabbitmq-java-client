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
     */
    LongString handleChallenge(LongString challenge, String username, String password);
}
