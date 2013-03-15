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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2013-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import java.util.Arrays;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

// requires
// rabbitmqctl set_policy cache-me "^lvc-test-exchange$" '{"lvc": 1}' 1

public class LvcExchange  extends BrokerTestCase {

    private final static String exhange = "lvc-test-exchange";
    private final static String queue = "lvc-test-queue";
    private final static byte[] payload = "LVC test payload".getBytes();

    public void testBindAfterPublish() throws Exception {
        channel.queueDeclare(queue, false, true, true, null);
        channel.exchangeDeclare(exhange, "direct");
        channel.basicPublish(exhange, "testrk", null, payload);
        channel.queueBind(queue, exhange, "testrk");
        GetResponse resp = channel.basicGet(queue, true);
        channel.exchangeDelete(exhange);
        assertNotNull(resp);
        assertTrue(Arrays.equals(payload, resp.getBody()));
    }
}

