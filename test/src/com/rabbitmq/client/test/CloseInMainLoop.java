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

package com.rabbitmq.client.test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.SocketFactory;

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

  class SpecialConnection extends AMQConnection{
    private AtomicBoolean validShutdown = new AtomicBoolean(false);

    public boolean hadValidShutdown(){
      if(isOpen()) throw new IllegalStateException("hadValidShutdown called while connection is still open");
      return validShutdown.get();
    }

    public SpecialConnection() throws Exception {
        this(new ConnectionFactory());
    }

    private SpecialConnection(ConnectionFactory factory) throws Exception{
      super(factory.getUsername(),
            factory.getPassword(),
            new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", AMQP.PROTOCOL.PORT)),
            Executors.newFixedThreadPool(1),
            factory.getVirtualHost(),
            factory.getClientProperties(),
            factory.getRequestedFrameMax(),
            factory.getRequestedChannelMax(),
            factory.getRequestedHeartbeat(),
            factory.getSaslConfig(),
            new DefaultExceptionHandler(){
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

        this.start();
      }

    @Override
    public boolean processControlCommand(Command c) throws IOException{
      if(c.getMethod() instanceof AMQP.Connection.CloseOk) validShutdown.set(true);
      return super.processControlCommand(c);
    }

  }

  public void testCloseOKNormallyReceived() throws Exception{
    SpecialConnection connection = new SpecialConnection();
    connection.close();
    assertTrue(connection.hadValidShutdown());
  }

  // The thrown runtime exception should get intercepted by the
  // consumer exception handler, and result in a clean shut down.
  public void testCloseWithFaultyConsumer() throws Exception{
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
