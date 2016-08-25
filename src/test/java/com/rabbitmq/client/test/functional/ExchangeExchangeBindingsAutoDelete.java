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

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

public class ExchangeExchangeBindingsAutoDelete extends BrokerTestCase {

    protected void declareExchanges(String[] names) throws IOException {
        for (String e : names) {
            channel.exchangeDeclare(e, "fanout", false, true, null);
        }
    }

    protected void assertExchangesNotExist(String[] names) throws IOException {
        for (String e : names) {
            assertExchangeNotExists(e);
        }
    }

    protected void assertExchangeNotExists(String name) throws IOException {
        try {
            connection.createChannel().exchangeDeclarePassive(name);
            fail("Exchange " + name + " still exists.");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
        }
    }

    /*
     * build (A -> B) and (B -> A) and then delete one binding and both
     * exchanges should autodelete
     */
    @Test public void autoDeleteExchangesSimpleLoop() throws IOException {
        String[] exchanges = new String[] {"A", "B"};
        declareExchanges(exchanges);
        channel.exchangeBind("A", "B", "");
        channel.exchangeBind("B", "A", "");

        channel.exchangeUnbind("A", "B", "");
        assertExchangesNotExist(exchanges);
    }

    /*
     * build (A -> B) (B -> C) (C -> D) and then delete D. All should autodelete
     */
    @Test public void transientAutoDelete() throws IOException {
        String[] exchanges = new String[] {"A", "B", "C", "D"};
        declareExchanges(exchanges);
        channel.exchangeBind("B", "A", "");
        channel.exchangeBind("C", "B", "");
        channel.exchangeBind("D", "C", "");

        channel.exchangeDelete("D");
        assertExchangesNotExist(exchanges);
    }

    /*
     * build (A -> B) (B -> C) (C -> D) (Source -> A) (Source -> B) (Source ->
     * C) (Source -> D) On removal of D, all should autodelete
     */
    @Test public void repeatedTargetAutoDelete() throws IOException {
        String[] exchanges = new String[] {"A", "B", "C", "D"};
        declareExchanges(exchanges);
        channel.exchangeDeclare("Source", "fanout", false, true, null);

        channel.exchangeBind("B", "A", "");
        channel.exchangeBind("C", "B", "");
        channel.exchangeBind("D", "C", "");

        for (String e : exchanges) {
            channel.exchangeBind(e, "Source", "");
        }

        channel.exchangeDelete("A");
        // Source should still be there. We'll verify this by redeclaring
        // it here and verifying it goes away later
        channel.exchangeDeclare("Source", "fanout", false, true, null);

        channel.exchangeDelete("D");
        assertExchangesNotExist(exchanges);
        assertExchangeNotExists("Source");
    }
    
    /*
     * build (A -> B) (B -> C) (A -> C). Delete C and they should all vanish
     */
    @Test public void autoDeleteBindingToVanishedExchange() throws IOException {
        String[] exchanges = new String[] {"A", "B", "C"};
        declareExchanges(exchanges);
        channel.exchangeBind("C", "B", "");
        channel.exchangeBind("B", "A", "");
        channel.exchangeBind("C", "A", "");
        channel.exchangeDelete("C");
        assertExchangesNotExist(exchanges);
    }

}
