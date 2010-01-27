package com.rabbitmq.client.test;

import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import javax.net.SocketFactory;

public class CloseInMainLoop extends BrokerTestCase{
  class SpecialConnection extends AMQConnection{
    private AtomicBoolean validShutdown = new AtomicBoolean();

    public boolean hadValidShutdown(){
      if(isOpen()) throw new IllegalStateException("hadValidShutdown called while connection is still open");
      return validShutdown.get();
    }
  
    public SpecialConnection() throws Exception{
      super(
          new ConnectionParameters(), 
          new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", 5672)),
          new DefaultExceptionHandler(){
            @Override public void handleConsumerException(Channel channel,
                                                           Throwable exception,
                                                           Consumer consumer,
                                                           String consumerTag,
                                                           String methodName){
                try {
                  ((AMQConnection) channel.getConnection()).close(AMQP.INTERNAL_ERROR,
                                                                  "Internal error in Consumer " +
                                                                    consumerTag,
                                                                  false,
                                                                  exception);
                } catch (IOException ioe) {
                    // Man, this clearly isn't our day.
                    // Ignore the exception? TODO: Log the nested failure
                }
            }
        });

        this.start(true);
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
    channel.queueDeclare("q");
    channel.queueDelete("q");
    channel.queueDeclare("q");
    channel.queueBind("q", "x", "k");

    final CountDownLatch latch = new CountDownLatch(1);    

    channel.basicConsume("q", true, new DefaultConsumer(channel){
      public void handleDelivery(String consumerTag,
                                 Envelope envelope,
                                 AMQP.BasicProperties properties,
                                 byte[] body){
        latch.countDown();
        throw new RuntimeException("I am a bad consumer");
      }
    });

    channel.basicPublish("x", "k", null, new byte[10]);

    latch.await();
    Thread.sleep(200);
    assertTrue(connection.hadValidShutdown());
  }
  
}
