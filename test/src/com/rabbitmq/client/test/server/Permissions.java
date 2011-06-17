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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.server;

import com.rabbitmq.client.PossibleAuthenticationFailureException;
import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
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
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername("test");
        factory.setPassword("test");
        factory.setVirtualHost("/test");
        connectionFactory = factory;
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
        Host.rabbitmqctl("add_user test test");
        Host.rabbitmqctl("add_user testadmin test");
        Host.rabbitmqctl("add_vhost /test");
        Host.rabbitmqctl("set_permissions -p /test test configure write read");
        Host.rabbitmqctl("set_permissions -p /test testadmin \".*\" \".*\" \".*\"");
    }

    protected void deleteRestrictedAccount()
        throws IOException
    {
        Host.rabbitmqctl("clear_permissions -p /test testadmin");
        Host.rabbitmqctl("clear_permissions -p /test test");
        Host.rabbitmqctl("delete_vhost /test");
        Host.rabbitmqctl("delete_user testadmin");
        Host.rabbitmqctl("delete_user test");
    }

    protected void createResources()
        throws IOException
    {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername("testadmin");
        factory.setPassword("test");
        factory.setVirtualHost("/test");
        Connection connection = factory.newConnection();
        adminCh = connection.createChannel();
        withNames(new WithName() {
                public void with(String name) throws IOException {
                    adminCh.exchangeDeclare(name, "direct");
                    adminCh.queueDeclare(name, false, false, false, null);
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

    public void testAuth()
    {
        ConnectionFactory unAuthFactory = new ConnectionFactory();
        unAuthFactory.setUsername("test");
        unAuthFactory.setPassword("tset");

        try {
            unAuthFactory.newConnection();
            fail("Exception expected if password is wrong");
        } catch (IOException e) {
            assertTrue(e instanceof PossibleAuthenticationFailureException);
            String msg = e.getMessage();
            assertTrue("Exception message should contain 'auth'",
                       msg.toLowerCase().contains("auth"));
        }
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
                    channel.exchangeDelete(name);
                }});
    }

    public void testQueueConfiguration()
        throws IOException
    {
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDeclare(name, false, false, false, null);
                }});
        runConfigureTest(new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDelete(name);
                }});
    }

    public void testPassiveDeclaration() throws IOException {
        adminCh.exchangeDeclare("xxx", "direct");
        adminCh.queueDeclare("xxx", false, false, false, null);
        // We have no permissions on these but should be able to passive
        // declare them anyway
        channel.exchangeDeclarePassive("xxx");
        channel.queueDeclarePassive("xxx");
        adminCh.exchangeDelete("xxx");
        adminCh.queueDelete("xxx");
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

    public void testAltExchConfiguration()
        throws IOException
    {
        runTest(false, false, false,
                createAltExchConfigTest("configure-me"));
        runTest(false, false, false,
                createAltExchConfigTest("configure-and-write-me"));
        runTest(false, true, false,
                createAltExchConfigTest("configure-and-read-me"));
    }

    public void testNoAccess()
        throws IOException, InterruptedException
    {
        Host.rabbitmqctl("set_permissions -p /test test \"\" \"\" \"\"");
        Thread.sleep(2000);

        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.queueDeclare();
                }}
        );

        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.queueDeclare("justaqueue", false, false, true, null);
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.queueDelete("configure");
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.queueBind("write", "write", "write");
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.queuePurge("read");
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.exchangeDeclare("justanexchange", "direct");
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.exchangeDeclare("configure", "direct");
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.basicPublish("write", "", null, "foo".getBytes());
                    channel.queueDeclare();
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.basicGet("read", false);
                }}
        );
        expectExceptionRun(AMQP.ACCESS_REFUSED, new WithName() {
                public void with(String _) throws IOException {
                    channel.basicConsume("read", null);
                }}
        );
    }

    protected void expectExceptionRun(int exceptionCode, WithName action)
        throws IOException
    {
        try {
            action.with("");
            fail();
        } catch (IOException e) {
            ShutdownSignalException sse = (ShutdownSignalException)e.getCause();
            if (sse.isHardError()) {
                fail("Got a hard-error.  Was expecting soft-error: " + exceptionCode);
            } else {
                AMQP.Channel.Close closeMethod =
                    (AMQP.Channel.Close) ((Command)sse.getReason()).getMethod();
                assertEquals(exceptionCode, closeMethod.getReplyCode());
            }
            channel = channel.getConnection().createChannel();
        }
    }

    protected WithName createAltExchConfigTest(final String exchange)
        throws IOException
    {
        return new WithName() {
            public void with(String ae) throws IOException {
                Map<String, Object> args = new HashMap<String, Object>();
                args.put("alternate-exchange", ae);
                channel.exchangeDeclare(exchange, "direct", false, false, args);
                channel.exchangeDelete(exchange);
            }};
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
        String msg = "'" + name + "' -> " + exp;
        try {
            test.with(name);
            assertTrue(msg, exp);
        } catch (IOException e) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
            openConnection();
            openChannel();
        }
    }

    public interface WithName {
        public void with(String name) throws IOException;
    }

}
