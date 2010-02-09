package com.rabbitmq.examples;

import com.rabbitmq.client.*;
import java.util.concurrent.*;
import java.util.Random;

class RandomizedConcurrencyTests{

  public int port = 5672;
  public String host = "localhost";
  public int queueExchangeCount = 1000; 
  public int threadCount = 20;

  private final Object lock = new Object();

  public RandomizedConcurrencyTests() throws Exception{
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


  public void run(){
    final Connection conn;
    try {
      conn = new ConnectionFactory().newConnection(host, port);
      Channel setup = conn.createChannel();

      for(int i = 0; i < queueExchangeCount; i++){
        setup.exchangeDeclare(exchange(i), "direct");
        setup.queueDeclare(queue(i));
        setup.queueBind(queue(i),exchange(i), "");
      }
      
      setup.close();
    } catch(Exception e){
      e.printStackTrace();
      System.exit(1);
      throw null; // placate the compiler
    }

    for(int i = 0; i < threadCount; i++){
      final int j = i;
      new Thread(){
        @Override public void run(){
          try {
            Random rnd = new Random();
            Channel ch = conn.createChannel();
            while(true){
              switch(rnd.nextInt(3)){
                case 0: 
                  Channel old = ch;
                  ch.close();
                  ch = conn.createChannel(); 
                break;
                case 1:
                  ch.basicPublish(
                    exchange(rnd.nextInt(queueExchangeCount)), 
                    "", null,
                    new byte[1024 * 1024]
                  );
                break;
                case 2:
                  ch.basicGet(queue(rnd.nextInt(queueExchangeCount)), true);
                break;
              }
            }
          } catch(Exception e){
            synchronized(lock){
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
