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

package com.rabbitmq.client.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.SocketFactory;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;

public class CloseInMainLoop extends BrokerTestCase{
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    private ConnectionFactory specialConnectionFactory() {
        ConnectionFactory f = TestUtils.connectionFactory();
        f.setExceptionHandler(new DefaultExceptionHandler(){
            @Override
            public void handleConsumerException(Channel channel,
                                                Throwable exception,
                                                Consumer consumer,
                                                String consumerTag,
                                                String methodName) {
                try {
                    // TODO: change this to call 4-parameter close and make 6-parm one private
                    ((AMQConnection) channel.getConnection())
                            .close(AMQP.INTERNAL_ERROR,
                                    "Internal error in Consumer " + consumerTag,
                                    false,
                                    exception,
                                    -1,
                                    false);
                } catch (Throwable e) {
                    // Man, this clearly isn't our day.
                    // TODO: Log the nested failure
                } finally {
                    closeLatch.countDown();
                }
            }
        });
        return f;
    }

    class SpecialConnection extends AMQConnection{
    private final AtomicBoolean validShutdown = new AtomicBoolean(false);

    public boolean hadValidShutdown(){
      if(isOpen()) throw new IllegalStateException("hadValidShutdown called while connection is still open");
      return validShutdown.get();
    }

    public SpecialConnection() throws Exception {
        super(specialConnectionFactory().params(Executors.newFixedThreadPool(1)),
              new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", AMQP.PROTOCOL.PORT)));
        this.start();
    }

    @Override
    public boolean processControlCommand(Command c) throws IOException{
      if(c.getMethod() instanceof AMQP.Connection.CloseOk) validShutdown.set(true);
      return super.processControlCommand(c);
    }
  }

  @Test public void closeOKNormallyReceived() throws Exception{
    SpecialConnection connection = new SpecialConnection();
    connection.close();
    assertTrue(connection.hadValidShutdown());
  }

  // The thrown runtime exception should get intercepted by the
  // consumer exception handler, and result in a clean shut down.
  @Test public void closeWithFaultyConsumer() throws Exception{
    SpecialConnection connection = new SpecialConnection();
    Channel channel = connection.createChannel();
    channel.exchangeDeclare("x", "direct");
    channel.queueDeclare("q", false, false, false, null);
    channel.queueBind("q", "x", "k");

    channel.basicConsume("q", true, new DefaultConsumer(channel){
        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body) {
            throw new RuntimeException("I am a bad consumer");
        }
    });

    channel.basicPublish("x", "k", null, new byte[10]);

    assertTrue(closeLatch.await(1000, TimeUnit.MILLISECONDS));
    assertTrue(connection.hadValidShutdown());
  }

}
