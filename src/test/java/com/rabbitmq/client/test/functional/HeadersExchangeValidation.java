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

package com.rabbitmq.client.test.functional;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.BrokerTestCase;

public class HeadersExchangeValidation extends BrokerTestCase {

    @Test public void headersValidation() throws IOException
    {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare();
        String queue = ok.getQueue();

        HashMap<String, Object> arguments = new HashMap<String, Object>();
        succeedBind(queue, arguments);

        arguments.put("x-match", 23);
        failBind(queue, arguments);

        arguments.put("x-match", "all or any I don't mind");
        failBind(queue, arguments);

        arguments.put("x-match", "all");
        succeedBind(queue, arguments);

        arguments.put("x-match", "any");
        succeedBind(queue, arguments);
    }

    private void failBind(String queue, HashMap<String, Object> arguments) {
        try {
            Channel ch = connection.createChannel();
            ch.queueBind(queue, "amq.headers", "", arguments);
            fail("Expected failure");
        } catch (IOException e) {
	        checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    private void succeedBind(String queue, HashMap<String, Object> arguments) throws IOException {
        Channel ch = connection.createChannel();
        ch.queueBind(queue, "amq.headers", "", arguments);
        ch.abort();
    }
}
