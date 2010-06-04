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
package com.rabbitmq.client.test;

import com.rabbitmq.client.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
