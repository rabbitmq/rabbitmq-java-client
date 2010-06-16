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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test;

import junit.framework.TestCase;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;

import com.rabbitmq.client.AMQP;

public class BrokerTestCase extends TestCase
{
    public ConnectionFactory connectionFactory = new ConnectionFactory();

    public Connection connection;
    public Channel channel;

    protected void setUp() {
        openConnection();
        openChannel();

        createResources();
    }

    protected void tearDown() {
        closeChannel();
        closeConnection();

        openConnection();
        openChannel();
        releaseResources();
        closeChannel();
        closeConnection();
    }

    /**
     * Should create any AMQP resources needed by the test. Will be
     * called by BrokerTestCase's implementation of setUp, after the
     * connection and channel have been opened.
     */
    protected void createResources() {}

    /**
     * Should destroy any AMQP resources that were created by the
     * test. Will be called by BrokerTestCase's implementation of
     * tearDown, after the connection and channel have been closed and
     * reopened specifically for this method. After this method
     * completes, the connection and channel will be closed again.
     */
    protected void releaseResources() {}

    public void openConnection() {
        if (connection == null) {
            connection = connectionFactory.newConnection();
        }
    }

    public void closeConnection() {
        if (connection != null) {
            connection.abort();
            connection = null;
        }
    }

    public void openChannel() {
        channel = connection.createChannel();
    }

    public void closeChannel() {
        if (channel != null) {
            channel.abort();
            channel = null;
        }
    }

    public void checkShutdownSignal(int expectedCode, IOException ioe) {
        ShutdownSignalException sse = (ShutdownSignalException) ioe.getCause();
        Command closeCommand = (Command) sse.getReason();
        channel = null;
        if (sse.isHardError()) {
            connection = null;
            AMQP.Connection.Close closeMethod = (AMQP.Connection.Close) closeCommand.getMethod();
            assertEquals(expectedCode, closeMethod.getReplyCode());
        } else {
            AMQP.Channel.Close closeMethod = (AMQP.Channel.Close) closeCommand.getMethod();
            assertEquals(expectedCode, closeMethod.getReplyCode());
        }
    }

  protected void assertDelivered(String q, int count) throws IOException {
    assertDelivered(q, count, false);
  }

  protected void assertDelivered(String q, int count, boolean redelivered) throws IOException {
    GetResponse r;
    for (int i = 0; i < count; i++) {
      r = basicGet(q);
      assertNotNull(r);
      assertEquals(r.getEnvelope().isRedeliver(), redelivered);
    }
    assertNull(basicGet(q));
  }

  protected GetResponse basicGet(String q) throws IOException {
    return channel.basicGet(q, true);
  }

  protected void basicPublishPersistent(String q) throws IOException {
      basicPublishPersistent("persistent message".getBytes(), q);
  }

  protected void basicPublishPersistent(byte[] msg, String q) throws IOException {
      basicPublishPersistent(msg, "", q);
  }

  protected void basicPublishPersistent(String x, String routingKey) throws IOException {
      basicPublishPersistent("persistent message".getBytes(), x, routingKey);
    }


  protected void basicPublishPersistent(byte[] msg, String x, String routingKey) throws IOException {
      channel.basicPublish(x, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, msg);
    }

  protected void basicPublishVolatile(String q) throws IOException {
      basicPublishVolatile("not persistent message".getBytes(), q);
  }

  protected void basicPublishVolatile(byte[] msg, String q) throws IOException {
      basicPublishVolatile(msg, "", q);
  }

  protected void basicPublishVolatile(String x, String routingKey) throws IOException {
      basicPublishVolatile("not persistent message".getBytes(), x, routingKey);
  }

  protected void basicPublishVolatile(byte[] msg, String x, String routingKey) throws IOException {
      channel.basicPublish(x, routingKey, MessageProperties.TEXT_PLAIN, msg);
  }

  protected void declareAndBindDurableQueue(String q, String x, String r) throws IOException {
    declareDurableQueue(q);
    channel.queueBind(q, x, r);
  }

  protected void declareDurableDirectExchange(String x) throws IOException {
    channel.exchangeDeclare(x, "direct", true);
  }

  protected void declareDurableQueue(String q) throws IOException {
    channel.queueDeclare(q, true);
  }

  protected void declareDurableTopicExchange(String x) throws IOException {
    channel.exchangeDeclare(x, "topic", true);
  }

  protected void deleteExchange(String x) throws IOException {
    channel.exchangeDelete(x);
  }

  protected void deleteQueue(String q) throws IOException {
    channel.queueDelete(q);
  }

}
