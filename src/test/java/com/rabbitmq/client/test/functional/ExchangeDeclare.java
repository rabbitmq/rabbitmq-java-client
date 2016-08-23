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

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

public class ExchangeDeclare extends ExchangeEquivalenceBase {

    static final String TYPE = "direct";

    static final String NAME = "exchange_test";

    public void releaseResources() throws IOException {
        channel.exchangeDelete(NAME);
    }

    public void testExchangeNoArgsEquivalence() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        verifyEquivalent(NAME, TYPE, false, false, null);
    }

    public void testExchangeNonsenseArgsEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("nonsensical-argument-surely-not-in-use", "foo");
        verifyEquivalent(NAME, TYPE, false, false, args);
    }

    public void testExchangeDurableNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, TYPE, false, false, null);
        verifyNotEquivalent(NAME, TYPE, true, false, null);
    }

    public void testExchangeTypeNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, "direct", false, false, null);
        verifyNotEquivalent(NAME, "fanout", false, false, null);
    }

    public void testExchangeAutoDeleteNotEquivalent() throws IOException {
        channel.exchangeDeclare(NAME, "direct", false, false, null);
        verifyNotEquivalent(NAME, "direct", false, true, null);
    }
}
