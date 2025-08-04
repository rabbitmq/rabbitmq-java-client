// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for bug 19356 - SSL Support in rabbitmq
 *
 */
public class UnverifiedConnection extends BrokerTestCase {

    public void openConnection()
            throws IOException, TimeoutException {
        try {
            connectionFactory.useSslProtocol();
            TlsTestUtils.maybeConfigureNetty(connectionFactory, TlsTestUtils.ALWAYS_TRUST_MANAGER);
        } catch (Exception ex) {
            throw new IOException(ex);
        }

        int attempt = 0;
        while(attempt < 3) {
            try {
                connection = connectionFactory.newConnection();
                break;
            } catch(Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Error when opening TLS connection");
                attempt++;
            }
        }
        if(connection == null) {
            fail("Couldn't open TLS connection after 3 attempts");
        }
    }

    @Test public void sSL() throws IOException
    {
        channel.queueDeclare("Bug19356Test", false, true, true, null);
        channel.basicPublish("", "Bug19356Test", null, "SSL".getBytes());

        GetResponse chResponse = channel.basicGet("Bug19356Test", false);
        assertNotNull(chResponse);

        byte[] body = chResponse.getBody();
        assertEquals("SSL", new String(body));
    }
    
}
