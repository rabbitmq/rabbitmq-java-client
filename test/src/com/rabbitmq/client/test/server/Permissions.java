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


package com.rabbitmq.client.test.server;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.PossibleAuthenticationFailureException;
import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.impl.AMQChannel;
import com.rabbitmq.tools.Host;

public class Permissions extends BrokerTestCase
{

    private Channel adminCh;

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
        deleteRestrictedAccount();
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
        Host.rabbitmqctlIgnoreErrors("clear_permissions -p /test testadmin");
        Host.rabbitmqctlIgnoreErrors("clear_permissions -p /test test");
        Host.rabbitmqctlIgnoreErrors("delete_vhost /test");
        Host.rabbitmqctlIgnoreErrors("delete_user testadmin");
        Host.rabbitmqctlIgnoreErrors("delete_user test");
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
        action.with("none");
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
        runTest(true, true, true, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.exchangeDeclarePassive(name);
                }});
        runTest(true, true, true, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDeclarePassive(name);
                }});
    }

    public void testBinding()
        throws IOException
    {
        runTest(false, true, false, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.queueBind(name, "read", "");
                }});
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.queueBind("write", name, "");
                }});
    }

    public void testPublish()
        throws IOException
    {
        runTest(false, true, false, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicPublish(name, "", null, "foo".getBytes());
                    //followed by a dummy synchronous command in order
                    //to catch any errors
                    channel.basicQos(0);
                }});
    }

    public void testGet()
        throws IOException
    {
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicGet(name, true);
                }});
    }

    public void testConsume()
        throws IOException
    {
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicConsume(name, new QueueingConsumer(channel));
                }});
    }

    public void testPurge()
        throws IOException
    {
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    ((AMQChannel)channel)
                    .exnWrappingRpc(new AMQP.Queue.Purge.Builder()
                                                        .queue(name)
                                    .build());
                }});
    }

    public void testAltExchConfiguration()
        throws IOException
    {
        runTest(false, false, false, false,
                createAltExchConfigTest("configure-me"));
        runTest(false, false, false, false,
                createAltExchConfigTest("configure-and-write-me"));
        runTest(false, true, false, false,
                createAltExchConfigTest("configure-and-read-me"));
    }

    public void testNoAccess()
        throws IOException, InterruptedException
    {
        Host.rabbitmqctl("set_permissions -p /test test \"\" \"\" \"\"");
        Thread.sleep(2000);

        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.queueDeclare();
            }
        }
        );

        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.queueDeclare("justaqueue", false, false, true, null);
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.queueDelete("configure");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.queueBind("write", "write", "write");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.queuePurge("read");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.exchangeDeclare("justanexchange", "direct");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.exchangeDeclare("configure", "direct");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.basicPublish("write", "", null, "foo".getBytes());
                channel.basicQos(0);
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.basicGet("read", false);
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _) throws IOException {
                channel.basicConsume("read", null);
            }
        }
        );
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
        runTest(false, "none", test);
    }

    protected void runTest(boolean expC, boolean expW, boolean expR, boolean expN,
                           WithName test)
        throws IOException
    {
        runTest(expC, "configure", test);
        runTest(expW, "write", test);
        runTest(expR, "read", test);
        runTest(expN, "none", test);
    }

    protected void assertAccessRefused(WithName test) throws IOException {
        runTest(false, "", test);
    }

    protected void runTest(boolean exp, String name, WithName test)
        throws IOException
    {
        String msg = "'" + name + "' -> " + exp;
        try {
            test.with(name);
            assertTrue(msg, exp);
        } catch (IOException e) {
            assertFalse(msg, exp);
            checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
            openChannel();
        } catch (AlreadyClosedException e) {
            assertFalse(msg, exp);
            checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
            openChannel();
        }
    }

    private interface WithName {
        public void with(String name) throws IOException;
    }

}
