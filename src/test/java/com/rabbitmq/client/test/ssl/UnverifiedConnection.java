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
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//

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
