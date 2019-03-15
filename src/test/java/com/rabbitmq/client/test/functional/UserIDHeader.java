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
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

public class UserIDHeader extends BrokerTestCase {
    private static final AMQP.BasicProperties GOOD = new AMQP.BasicProperties.Builder().userId("guest").build();
    private static final AMQP.BasicProperties BAD = new AMQP.BasicProperties.Builder().userId("not the guest, honest").build();

    @Test public void validUserId() throws IOException {
        publish(GOOD);
    }

    @Test public void invalidUserId() {
        try {
            publish(BAD);
            fail("Accepted publish with incorrect user ID");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        } catch (AlreadyClosedException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void impersonatedUserId() throws IOException, TimeoutException {
        Host.rabbitmqctl("set_user_tags guest administrator impersonator");
        try (Connection c = connectionFactory.newConnection()){
            publish(BAD, c.createChannel());
        } finally {
            Host.rabbitmqctl("set_user_tags guest administrator");
        }
    }

    private void publish(AMQP.BasicProperties properties) throws IOException {
        publish(properties, this.channel);
    }

    private void publish(AMQP.BasicProperties properties, Channel channel) throws IOException {
        channel.basicPublish("amq.fanout", "", properties, "".getBytes());
        channel.queueDeclare(); // To flush the channel
    }
}
