package com.rabbitmq.examples;

import com.rabbitmq.client.*;
import java.util.concurrent.*;
import java.util.Random;

/** 
 * Test that the tracer correctly handles multiple concurrently processing
 * channels. If it doesn't then this code will cause the tracer to break
 * and will be disconneted.
 */
public class TracerConcurrencyTest{

  public int port = 5673;
  public String host = "localhost";
  public int threadCount = 3;

  private final Object lock = new Object();

  public static void main(String[] args) throws Exception{
    new TracerConcurrencyTest().run();
  }

  static String EXCHANGE = "tracer-exchange";
  static String QUEUE = "tracer-queue";

  public void run(){

    final Connection conn;
    try {
      conn = new ConnectionFactory().newConnection(host, port);
      Channel setup = conn.createChannel();

      setup.exchangeDeclare(EXCHANGE, "direct");
      setup.queueDeclare(QUEUE, false, false, false, null);
      setup.queueBind(QUEUE,EXCHANGE, "");
      
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
                Channel old = ch;
                ch.close();
                ch = conn.createChannel(); 
                ch.basicPublish(
                  EXCHANGE,
                  "", null,
                  new byte[1024 * 1024]
                );
                ch.basicGet(QUEUE, true);
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
