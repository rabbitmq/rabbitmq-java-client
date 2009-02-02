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

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQChannel;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.tools.Host;

public class Permissions extends BrokerTestCase
{

    protected Channel adminCh;

    public Permissions()
    {
        ConnectionParameters params = new ConnectionParameters();
        params.setUsername("test");
        params.setPassword("test");
        params.setVirtualHost("/test");
        connectionFactory = new ConnectionFactory(params);
    }

    protected void setUp()
        throws IOException
    {
        addRestrictedAccount();
        super.setUp();
    }

    protected void tearDown()
        throws IOException
    {
        super.tearDown();
        deleteRestrictedAccount();
    }

    protected void addRestrictedAccount()
        throws IOException
    {
        runCtl("add_user test test");
        runCtl("add_user testadmin test");
        runCtl("add_vhost /test");
        runCtl("set_permissions -p /test test configure write read");
        runCtl("set_permissions -p /test testadmin \".*\" \".*\" \".*\"");
    }

    protected void deleteRestrictedAccount()
        throws IOException
    {
        runCtl("clear_permissions -p /test testadmin");
        runCtl("clear_permissions -p /test test");
        runCtl("delete_vhost /test");
        runCtl("delete_user testadmin");
        runCtl("delete_user test");
    }

    protected void runCtl(String command)
        throws IOException
    {
        Host.executeCommand("../rabbitmq-server/scripts/rabbitmqctl " +
                            command);
    }

    protected void createResources()
        throws IOException
    {
        ConnectionParameters params = new ConnectionParameters();
        params.setUsername("testadmin");
        params.setPassword("test");
        params.setVirtualHost("/test");
        ConnectionFactory factory = new ConnectionFactory(params);
        Connection connection = factory.newConnection("localhost");
        adminCh = connection.createChannel();
        withNames(new WithName() {
                public void with(String name) throws IOException {
                    adminCh.exchangeDeclare(name, "direct");
                    adminCh.queueDeclare(name);
                }});
    }

    protected void releaseResources()
        throws IOException
    {
        withNames(new WithName() {
                public void with(String name) throws IOException {
                    adminCh.queueDelete(name);
                    adminCh.exchangeDelete(name);
                }});
        adminCh.getConnection().abort();
    }

    protected void withNames(WithName action)
        throws IOException
    {
        action.with("configure");
        action.with("write");
        action.with("read");
    }

    public void testExchangeConfiguration()
        throws IOException
    {
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.exchangeDeclare(name, "direct");
                }});
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.exchangeDeclare(name, "direct", true, false, false, null);
                }});
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.exchangeDelete(name);
                }});
    }

    public void testQueueConfiguration()
        throws IOException
    {
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDeclare(name);
                }});
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDeclare(name, true, false, false, false, null);
                }});
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDelete(name);
                }});
    }

    public void testBinding()
        throws IOException
    {
        runTest(false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.queueBind(name, "read", "");
                }});
        runTest(false, false, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.queueBind("write", name, "");
                }});
    }

    public void testPublish()
        throws IOException
    {
        runTest(false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicPublish(name, "", null, "foo".getBytes());
                    //followed by a dummy synchronous command in order
                    //to catch any errors
                    ((AMQChannel)channel).exnWrappingRpc(new AMQImpl.Channel.Flow(true));
                }});
    }

    public void testGet()
        throws IOException
    {
        runTest(false, false, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicGet(name, true);
                }});
    }

    public void testConsume()
        throws IOException
    {
        runTest(false, false, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicConsume(name, new QueueingConsumer(channel));
                }});
    }

    public void testPurge()
        throws IOException
    {
        runTest(false, false, true, new WithName() {
                public void with(String name) throws IOException {
                    ((AMQChannel)channel).exnWrappingRpc(new AMQImpl.Queue.Purge(0, name, false));
                }});
    }

    protected void runConfigureTest(WithName test)
        throws IOException
    {
        runTest(true, "configure-me", test);
        runTest(false, "write-me", test);
        runTest(false, "read-me", test);
    }

    protected void runTest(boolean expC, boolean expW, boolean expR,
                           WithName test)
        throws IOException
    {
        runTest(expC, "configure", test);
        runTest(expW, "write", test);
        runTest(expR, "read", test);
    }

    protected void runTest(boolean exp, String name, WithName test)
        throws IOException
    {
        try {
            test.with(name);
            assertTrue(exp);
        } catch (IOException e) {
            assertFalse(exp);
            Throwable t = e.getCause();
            assertTrue(t instanceof ShutdownSignalException);
            Object r = ((ShutdownSignalException)t).getReason();
            assertTrue(r instanceof Command);
            Method m = ((Command)r).getMethod();
            assertTrue(m instanceof AMQP.Channel.Close);
            assertEquals(AMQP.ACCESS_REFUSED,
                         ((AMQP.Channel.Close)m).getReplyCode());
            //This fails due to bug 20296
            //openChannel();
            channel = connection.createChannel(channel.getChannelNumber() + 1);
        }
    }

    public interface WithName {
        public void with(String name) throws IOException;
    }

}
