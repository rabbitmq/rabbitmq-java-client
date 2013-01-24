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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import java.io.IOException;

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
    public void testAutoDeleteExchangesSimpleLoop() throws IOException {
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
    public void testTransientAutoDelete() throws IOException {
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
    public void testRepeatedTargetAutoDelete() throws IOException {
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
    public void testAutoDeleteBindingToVanishedExchange() throws IOException {
        String[] exchanges = new String[] {"A", "B", "C"};
        declareExchanges(exchanges);
        channel.exchangeBind("C", "B", "");
        channel.exchangeBind("B", "A", "");
        channel.exchangeBind("C", "A", "");
        channel.exchangeDelete("C");
        assertExchangesNotExist(exchanges);
    }

}
