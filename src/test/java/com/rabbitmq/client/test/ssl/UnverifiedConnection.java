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

package com.rabbitmq.client.test.ssl;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

/**
 * Test for bug 19356 - SSL Support in rabbitmq
 *
 */
public class UnverifiedConnection extends BrokerTestCase {
    public void openConnection()
            throws IOException, TimeoutException {
        try {
            connectionFactory.useSslProtocol();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex.toString());
        } catch (KeyManagementException ex) {
            throw new IOException(ex.toString());
        }

        if (connection == null) {
            connection = connectionFactory.newConnection();
        }
    }

    public void testSSL() throws IOException
    {
        channel.queueDeclare("Bug19356Test", false, true, true, null);
        channel.basicPublish("", "Bug19356Test", null, "SSL".getBytes());

        GetResponse chResponse = channel.basicGet("Bug19356Test", false);
        assertNotNull(chResponse);

        byte[] body = chResponse.getBody();
        assertEquals("SSL", new String(body));
    }
    
}
