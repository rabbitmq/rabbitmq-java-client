package com.rabbitmq.examples;

import com.rabbitmq.client.*;
import java.util.concurrent.*;
import java.util.Random;

class RandomizedConcurrencyTests{

  final Connection conn;

  public RandomizedConcurrencyTests() throws Exception{
    conn = new ConnectionFactory().newConnection("localhost");
  }

  public static void main(String[] args) throws Exception{
    new RandomizedConcurrencyTests().run();
  }

  private String queue(int i){
    return "rct-queue-" + i;
  }

  private String exchange(int i){
    return "rct-exchange-" + i;
  }

  int QUEUE_EXCHANGE_COUNT = 1000;

  private final Object LOCK = new Object();

  public void run(){
    try {
      Channel setup = conn.createChannel();

      for(int i = 0; i < QUEUE_EXCHANGE_COUNT; i++){
        setup.exchangeDeclare(exchange(i), "direct");
        setup.queueDeclare(queue(i));
        setup.queueBind(queue(i),exchange(i), "");
      }
      
      setup.close();
    } catch(Exception e){
      e.printStackTrace();
      System.exit(1);
    }

    for(int i = 0; i < 10; i++){
      final int j = i;
      new Thread(){
        @Override public void run(){
          try {
            Random rnd = new Random();
            Channel ch = conn.createChannel();
            while(true){
              switch(rnd.nextInt(4)){
                case 0: 
                  int old = ch.getChannelNumber();
                  ch.close();
                  ch = conn.createChannel(); 
                  int newNo = ch.getChannelNumber();
                  System.err.println("Thread " + j + " closed channel " + old + " now using channel " + newNo);
                break;
                case 1:
                  ch.basicPublish(
                    exchange(rnd.nextInt(QUEUE_EXCHANGE_COUNT)), 
                    "", null,
                    new byte[rnd.nextInt(1024 * 1024)]
                  );
                break;
                case 2:
                  ch.basicGet(queue(rnd.nextInt(QUEUE_EXCHANGE_COUNT)), true);
                break;

                case 3:
                  Thread.sleep(50);
                break;  
              }
            }
          } catch(Exception e){
            synchronized(LOCK){
              e.printStackTrace();
              System.err.println();
            }
            System.exit(1);
          } 
        }
      }.start();
    }
  }
}
