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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

public class QueueingConsumerShutdownTests extends BrokerTestCase{
  static final String QUEUE = "some-queue";
  static final int THREADS = 5;

  public void testNThreadShutdown() throws Exception{
    Channel channel = connection.createChannel();
    final QueueingConsumer c = new QueueingConsumer(channel);
    channel.queueDeclare(QUEUE, false, true, true, null);
    channel.basicConsume(QUEUE, c);
    final AtomicInteger count = new AtomicInteger(THREADS);
    final CountDownLatch latch = new CountDownLatch(THREADS);

    for(int i = 0; i < THREADS; i++){
      new Thread(){
        @Override public void run(){
          try {
            while(true){
                c.nextDelivery();
            }
          } catch (ShutdownSignalException sig) {
              count.decrementAndGet();
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            latch.countDown();
          }
        }
      }.start();
    }

    connection.close();

    // Far longer than this could reasonably take
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    assertEquals(0, count.get());
  }

}
