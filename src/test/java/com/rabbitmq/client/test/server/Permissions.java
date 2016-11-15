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

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.AMQChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.Host;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class Permissions extends BrokerTestCase
{

    private Channel adminCh;

    public Permissions()
    {
        ConnectionFactory factory = TestUtils.connectionFactory();
        factory.setUsername("test");
        factory.setPassword("test");
        factory.setVirtualHost("/test");
        connectionFactory = factory;
    }

    public void setUp()
            throws IOException, TimeoutException {
        deleteRestrictedAccount();
        addRestrictedAccount();
        super.setUp();
    }

    public void tearDown()
            throws IOException, TimeoutException {
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
            throws IOException, TimeoutException {
        ConnectionFactory factory = TestUtils.connectionFactory();
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

    @Test public void auth() throws TimeoutException {
        ConnectionFactory unAuthFactory = TestUtils.connectionFactory();
        unAuthFactory.setUsername("test");
        unAuthFactory.setPassword("tset");

        try {
            unAuthFactory.newConnection();
            fail("Exception expected if password is wrong");
        } catch (IOException e) {
            assertTrue(e instanceof AuthenticationFailureException);
            String msg = e.getMessage();
            assertTrue("Exception message should contain 'auth'",
                       msg.toLowerCase().contains("auth"));
        }
    }

    @Test public void exchangeConfiguration()
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

    @Test public void queueConfiguration()
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

    @Test public void passiveDeclaration() throws IOException {
        runTest(true, true, true, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.exchangeDeclarePassive(name);
                }});
        runTest(true, true, true, true, new WithName() {
                public void with(String name) throws IOException {
                    channel.queueDeclarePassive(name);
                }});
    }

    @Test public void binding()
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

    @Test public void publish()
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

    @Test public void get()
        throws IOException
    {
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicGet(name, true);
                }});
    }

    @Test public void consume()
        throws IOException
    {
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    channel.basicConsume(name, new QueueingConsumer(channel));
                }});
    }

    @Test public void purge()
        throws IOException
    {
        runTest(false, false, true, false, new WithName() {
                public void with(String name) throws IOException {
                    AMQChannel channelDelegate = (AMQChannel) ((AutorecoveringChannel)channel).getDelegate();
                    channelDelegate.exnWrappingRpc(new AMQP.Queue.Purge.Builder()
                                    .queue(name)
                                    .build());
                }});
    }

    @Test public void altExchConfiguration()
        throws IOException
    {
        runTest(false, false, false, false,
                createAltExchConfigTest("configure-me"));
        runTest(false, false, false, false,
                createAltExchConfigTest("configure-and-write-me"));
        runTest(false, true, false, false,
                createAltExchConfigTest("configure-and-read-me"));
    }

    @Test public void dLXConfiguration()
            throws IOException
    {
        runTest(false, false, false, false,
                createDLXConfigTest("configure-me"));
        runTest(false, false, false, false,
                createDLXConfigTest("configure-and-write-me"));
        runTest(false, true, false, false,
                createDLXConfigTest("configure-and-read-me"));
    }

    @Test public void noAccess()
        throws IOException, InterruptedException
    {
        Host.rabbitmqctl("set_permissions -p /test test \"\" \"\" \"\"");
        Thread.sleep(2000);

        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.queueDeclare();
            }
        }
        );

        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.queueDeclare("justaqueue", false, false, true, null);
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.queueDelete("configure");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.queueBind("write", "write", "write");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.queuePurge("read");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.exchangeDeclare("justanexchange", "direct");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.exchangeDeclare("configure", "direct");
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.basicPublish("write", "", null, "foo".getBytes());
                channel.basicQos(0);
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
                channel.basicGet("read", false);
            }
        }
        );
        assertAccessRefused(new WithName() {
            public void with(String _e) throws IOException {
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

    protected WithName createDLXConfigTest(final String queue)
            throws IOException
    {
        return new WithName() {
            public void with(String dlx) throws IOException {
                Map<String, Object> args = new HashMap<String, Object>();
                args.put("x-dead-letter-exchange", dlx);
                channel.queueDeclare(queue, false, false, false, args);
                channel.queueDelete(queue);
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
