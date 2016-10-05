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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class ExchangeDeclare extends ExchangeEquivalenceBase {

    static final String TYPE = "direct";

    static final String NAME = "exchange_test";

    public void releaseResources() throws IOException {
        channel.exchangeDelete(NAME);
    }

    @Test public void exchangeNoArgsEquivalence() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        verifyEquivalent(NAME, TYPE, false, false, null);
    }

    @Test public void singleLineFeedStrippedFromExchangeName() throws IOException {
        channel.exchangeDeclare("exchange_test\n", TYPE, false, false, null);
        verifyEquivalent(NAME, TYPE, false, false, null);
    }

    @Test public void multipleLineFeedsStrippedFromExchangeName() throws IOException {
        channel.exchangeDeclare("exchange\n_test\n", TYPE, false, false, null);
        verifyEquivalent(NAME, TYPE, false, false, null);
    }

    @Test public void multipleLineFeedAndCarriageReturnsStrippedFromExchangeName() throws IOException {
        channel.exchangeDeclare("e\nxc\rhange\n\r_test\n\r", TYPE, false, false, null);
        verifyEquivalent(NAME, TYPE, false, false, null);
    }

    @Test public void exchangeNonsenseArgsEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("nonsensical-argument-surely-not-in-use", "foo");
        verifyEquivalent(NAME, TYPE, false, false, args);
    }

    @Test public void exchangeDurableNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        verifyNotEquivalent(NAME, TYPE, true, false, null);
    }

    @Test public void exchangeTypeNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, "direct", false, false, null);
        verifyNotEquivalent(NAME, "fanout", false, false, null);
    }

    @Test public void exchangeAutoDeleteNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, "direct", false, false, null);
        verifyNotEquivalent(NAME, "direct", false, true, null);
    }

    @Test public void exchangeDeclaredWithEnumerationEquivalentOnNonRecoverableConnection() throws IOException, InterruptedException {
        doTestExchangeDeclaredWithEnumerationEquivalent(channel);
    }

    @Test public void exchangeDeclaredWithEnumerationEquivalentOnRecoverableConnection() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        connectionFactory.setTopologyRecoveryEnabled(false);
        Connection c = connectionFactory.newConnection();
        try {
            doTestExchangeDeclaredWithEnumerationEquivalent(c.createChannel());
        } finally {
            c.abort();
        }

    }

    private void doTestExchangeDeclaredWithEnumerationEquivalent(Channel channel) throws IOException, InterruptedException {
        assertEquals("There are 4 standard exchange types", 4, BuiltinExchangeType.values().length);
        for (BuiltinExchangeType exchangeType : BuiltinExchangeType.values()) {
            channel.exchangeDeclare(NAME, exchangeType);
            verifyEquivalent(NAME, exchangeType.getType(), false, false, null);
            channel.exchangeDelete(NAME);

            channel.exchangeDeclare(NAME, exchangeType, false);
            verifyEquivalent(NAME, exchangeType.getType(), false, false, null);
            channel.exchangeDelete(NAME);

            channel.exchangeDeclare(NAME, exchangeType, false, false, null);
            verifyEquivalent(NAME, exchangeType.getType(), false, false, null);
            channel.exchangeDelete(NAME);

            channel.exchangeDeclare(NAME, exchangeType, false, false, false, null);
            verifyEquivalent(NAME, exchangeType.getType(), false, false, null);
            channel.exchangeDelete(NAME);

            channel.exchangeDeclareNoWait(NAME, exchangeType, false, false, false, null);
            // no check, this one is asynchronous
            channel.exchangeDelete(NAME);
        }
    }
}
