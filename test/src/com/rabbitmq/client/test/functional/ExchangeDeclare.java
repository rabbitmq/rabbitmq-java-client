//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import com.rabbitmq.client.test.BrokerTestCase;

public class ExchangeDeclare extends BrokerTestCase {

    static final String TYPE = "direct";

    static final String NAME = "exchange_test";

    public void releaseResources() throws IOException {
        channel.exchangeDelete(NAME);
    }

    public static void verifyEquivalent(Channel channel, String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        channel.exchangeDeclare(name, type, durable, autoDelete, args);
    }

    // Note: this will close the channel
    public void verifyNotEquivalent(Channel channel, String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        try {
            channel.exchangeDeclare(name, type, durable, autoDelete, args);
            fail("Exchange was supposed to be not equivalent");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_ALLOWED, ioe);
            return;
        }
    }

    public void testExchangeNoArgsEquivalence() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        verifyEquivalent(channel, NAME, TYPE, false, false, null);
    }

    public void testExchangeNonsenseArgsEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("nonsensical-argument-surely-not-in-use", "foo");
        verifyEquivalent(channel, NAME, TYPE, false, false, args);
    }

    public void testExchangeDurableNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        verifyNotEquivalent(channel, NAME, TYPE, true, false, null);
    }

    public void testExchangeTypeNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, "direct", false, false, null);
        verifyNotEquivalent(channel, NAME, "fanout", false, false, null);
    }

    public void testExchangeAutoDeleteNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, "direct", false, false, null);
        verifyNotEquivalent(channel, NAME, "direct", false, true, null);
    }
}
