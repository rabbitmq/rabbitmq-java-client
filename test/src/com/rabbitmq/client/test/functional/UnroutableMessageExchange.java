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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.GetResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.HashMap;

public class UnroutableMessageExchange extends BrokerTestCase
{

    static private String[] resources = new String[]{"x","u","v"};
    static private String[] keys      = new String[]{"x","u","v","z"};

    private AtomicBoolean gotReturn = new AtomicBoolean();

    private static boolean[] expected(String key) {
        boolean[] expected = new boolean[resources.length];
        for (int i = 0; i < resources.length; i++) {
            expected[i] = resources[i].equals(key);
        }
        return expected;
    }

    protected void setUp() throws IOException {
        super.setUp();
        channel.setReturnListener(new ReturnListener() {
                public void handleBasicReturn(int replyCode,
                                              String replyText,
                                              String exchange,
                                              String routingKey,
                                              AMQP.BasicProperties properties,
                                              byte[] body)
                    throws IOException {
                    gotReturn.set(true);
                }
            });
    }

    protected void setupRouting(String x, String ume) throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        if (ume != null) args.put("ume", ume);
        channel.exchangeDeclare(x, "direct", false, false, false, args);
        channel.queueBind(x, x, x);
    }

    protected void publish(String key, boolean mandatory, boolean immediate)
        throws IOException {
        channel.basicPublish("x", key, mandatory, immediate, null,
                             "ume-test".getBytes());
    }

    protected void publish(String key) throws IOException {
        publish(key, false, false);
    }

    protected void checkGet(boolean[] expected) throws IOException {
        for (int i = 0; i < resources.length; i++) {
            String q = resources[i];
            GetResponse r = channel.basicGet(q, true);
            assertEquals("check " + q , expected[i], r != null);
        }
    }

    protected void check(String key, boolean mandatory, boolean immediate,
                         boolean[] expected, boolean ret)
        throws IOException {

        gotReturn.set(false);
        publish(key, mandatory, immediate);
        checkGet(expected);
        assertEquals(ret, gotReturn.get());
    }

    protected void check(String key, boolean[] expected, boolean ret)
        throws IOException {
        check(key, false, false, expected, ret);
    }

    protected void check(String key, boolean mandatory, boolean immediate,
                         boolean ret) throws IOException {
        check(key, mandatory, immediate, expected(key), ret);
    }

    protected void check(String key, boolean ret) throws IOException {
        check(key, false, false, ret);
    }

    public void testUme() throws IOException {

        for (String q : resources) {
            channel.queueDeclare(q, false, false, true, false, null);
        }

        //check various cases of missing UMEs - we expect to see some
        //warnings in the server logs

        boolean unrouted[] = new boolean[] {false, false, false};

        setupRouting("x", "u");
        check("x", false);           //no warning
        check("u", unrouted, false); //warning

        setupRouting("u", "v");
        check("u", false);           //no warning
        check("v", unrouted, false); //warning

        setupRouting("v", null);
        check("v", false);           //no warning
        check("z", unrouted, false); //no warning

        //routing with UMEs in place
        for (String k : keys) {
            //ordinary
            check(k, false);
            //mandatory
            check(k, true, false, !k.equals("x"));
            //immediate
            check(k, false, true, k.equals("x") ? unrouted : expected(k), true);
            /*            
            if (k.equals("x"))
                check(k, false, true, unrouted, true);
            else
                check(k, false, true, true);
            */
        }

        //tx
        channel.txSelect();
        for (String k : keys) {
            publish(k);
            checkGet(unrouted);
            channel.txRollback();
            checkGet(unrouted);
            publish(k);
            checkGet(unrouted);
            channel.txCommit();
            checkGet(expected(k));
        }

        //cleanup
        for (String r : resources) {
            channel.exchangeDelete(r);
            channel.queueDelete(r);
        }

    }

}
