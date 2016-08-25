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


package com.rabbitmq.client.test.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.rabbitmq.client.test.functional.ExchangeEquivalenceBase;

public class AlternateExchangeEquivalence extends ExchangeEquivalenceBase {
    static final Map<String, Object> args = new HashMap<String, Object>();
    {
        args.put("alternate-exchange", "UME");
    }

    @Test public void alternateExchangeEquivalence() throws IOException {
        channel.exchangeDeclare("alternate", "direct", false, false, args);
        verifyEquivalent("alternate", "direct", false, false, args);
    }

    @Test public void alternateExchangeNonEquivalence() throws IOException {
        channel.exchangeDeclare("alternate", "direct", false, false, args);
        Map<String, Object> altargs = new HashMap<String, Object>();
        altargs.put("alternate-exchange", "somewhere");
        verifyNotEquivalent("alternate", "direct", false, false, altargs);
    }
}
