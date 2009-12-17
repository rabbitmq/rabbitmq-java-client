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
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlternateExchange extends BrokerTestCase {

    static private String[] resources = new String[]{"x", "u", "v"};
    static private String[] keys = new String[]{"x", "u", "v", "z"};

    static private boolean unrouted[] = new boolean[]{false, false, false};

    private AtomicBoolean gotReturn = new AtomicBoolean();

    /**
     * Determine which of the queues in our test configuration we
     * expect a message with routing key <code>key</code> to get
     * delivered to: the queue (if any) named <code>key</code>.
     *
     * @param key the routing key of the message
     * @return an array of booleans that when zipped with {@link
     *         #resources} indicates whether the messages is expected to be
     *         routed to the respective queue
     */
    private static boolean[] expected(String key) {
        boolean[] expected = new boolean[resources.length];
        for (int i = 0; i < resources.length; i++) {
            expected[i] = resources[i].equals(key);
        }
        return expected;
    }

    @Override
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

    @Override
    protected void createResources() throws IOException {
        for (String q : resources) {
            channel.queueDeclare(q);
        }
    }

    @Override
    protected void releaseResources() throws IOException {
        for (String q : resources) {
            channel.queueDelete(q);
        }
    }

    /**
     * Declare an direct exchange <code>name</code> with an
     * alternate-exchange <code>ae</code> and bind the queue
     * <code>name</code> to it with a binding key of
     * <code>name</code>.
     *
     * @param name the name of the exchange to be created, and queue
     *             to be bound
     * @param ae   the name of the alternate-exchage
     */
    protected void setupRouting(String name, String ae) throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        if (ae != null) args.put("alternate-exchange", ae);
        channel.exchangeDeclare(name, "direct", false, false, false, args);
        channel.queueBind(name, name, name);
    }

    protected void setupRouting() throws IOException {
        setupRouting("x", "u");
        setupRouting("u", "v");
        setupRouting("v", "x");
    }

    protected void cleanup() throws IOException {
        for (String e : resources) {
            channel.exchangeDelete(e);
        }
    }

    protected void publish(String key, boolean mandatory, boolean immediate)
            throws IOException {
        channel.basicPublish("x", key, mandatory, immediate, null,
                "ae-test".getBytes());
    }

    protected void publish(String key) throws IOException {
        publish(key, false, false);
    }

    /**
     * Perform an auto-acking 'basic.get' on each of the queues named
     * in {@link #resources} and check whether a message can be
     * retrieved when expected.
     *
     * @param expected an array of booleans that is zipped with {@link
     *                 #resources} and indicates whether a messages is expected
     *                 to be retrievable from the respective queue
     */
    protected void checkGet(boolean[] expected) throws IOException {
        for (int i = 0; i < resources.length; i++) {
            String q = resources[i];
            GetResponse r = channel.basicGet(q, true);
            assertEquals("check " + q, expected[i], r != null);
        }
    }

    /**
     * Test whether a message is routed as expected.
     * <p/>
     * We publish a message to exchange 'x' with a routing key of
     * <code>key</code>, check whether the message (actually, any
     * message) can be retrieved from the queues named in {@link
     * #resources} when expected, and whether a 'basic.return' is
     * received when expected.
     *
     * @param key       the routing key of the message to be sent
     * @param mandatory whether the message should be marked as 'mandatory'
     * @param immediate whether the message should be marked as 'immediate'
     * @param expected  indicates which queues we expect the message to
     *                  get routed to
     * @param ret       whether a 'basic.return' is expected
     * @see #checkGet(boolean[])
     */
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

    /**
     * check various cases of missing AEs - we expect to see some
     * warnings in the server logs
     */
    public void testMissing() throws IOException {
        setupRouting("x", "u");
        check("x", false);           //no warning
        check("u", unrouted, false); //warning

        setupRouting("u", "v");
        check("u", false);           //no warning
        check("v", unrouted, false); //warning

        setupRouting("v", null);
        check("v", false);           //no warning
        check("z", unrouted, false); //no warning

        cleanup();
    }

    public void testAe() throws IOException {
        setupRouting();

        for (String k : keys) {
            //ordinary
            check(k, false);
            //mandatory
            check(k, true, false, k.equals("z"));
            //immediate
            check(k, false, true, unrouted, true);
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

        cleanup();
    }

    public void testCycleBreaking() throws IOException {
        setupRouting();
        check("z", false);
        cleanup();
    }

}
