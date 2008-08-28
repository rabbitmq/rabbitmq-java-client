package com.rabbitmq.client.test.performance;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Random;

public class ChurnTest {

    String[] bindings, queues;

    public static void main(String[] args) throws Exception {

        int b = 200;
        int q = 200;

        ChurnTest s = new ChurnTest();
        s.startTest(b,q);
    }

    private void startTest(int b, int q) throws Exception {
        bindings = generate(b, "b.",".*");
        queues = generate(q, "q-", "");

        String x = "x-" + System.currentTimeMillis();


        int bs  = 1000;

        final Connection con = new ConnectionFactory().newConnection("0.0.0.0", 5672);
        Channel channel = con.createChannel();
        channel.exchangeDeclare(1, x, "topic");

        Thread t1 = new Thread(new ProducerThread(con.createChannel(), x));
        Thread t2 = new Thread(new ConsumerThread(con.createChannel()));
        t1.start();
        t2.start();



        int cnt = 0;
        long start = System.currentTimeMillis();
        for (String binding : bindings) {
            for (String queue : queues) {
                channel.queueDeclare(1, queue);
                channel.queueBind(1, queue, x, binding);
                if ((++cnt % bs) == 0) {
                    long now = System.currentTimeMillis();                    
                    System.err.println("Rate = " + (now - start) + " millis / " + bs + " items");                    
                    Thread.sleep(1000);
                    start = now;
                }
            }
        }



        t1.interrupt();
        t2.interrupt();

        //channel.close(200, "adios");
        //con.close();
        System.exit(0);
    }

    private String[] generate(int z, String prefix, String postfix) {
        String[] s =  new String[z];
        Random r = new Random();
        for (int i = 0; i < z; i++) {
            s[i] = prefix + r.nextLong() + postfix;
        }
        return s;
    }

    class ConsumerThread implements Runnable, Consumer {

        Channel c;
        int msg = 0;

        ConsumerThread(Channel c) {
            this.c = c;
        }

        public void run() {

            for (String queue : queues) {
                try {
                    c.queueDeclare(1, queue);
                    c.basicConsume(1,queue,this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            long start = System.currentTimeMillis();

            while (true) {
                
                try {
                    Thread.sleep(1);
                }
                catch (InterruptedException e) {
                    long now = System.currentTimeMillis();
                    System.err.println("Consumer msg/sec: " + msg / ((now - start)/1000));
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void handleConsumeOk(String consumerTag) {
            //System.err.println("Subscription tag: " + consumerTag);
        }

        public void handleCancelOk(String consumerTag) {
        }

        public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        }

        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            msg++;
        }
    }

    class ProducerThread implements Runnable {

        Channel c;
        String x;

        ProducerThread(Channel c, String x) {
            this.c = c;
            this.x = x;
            try {
                c.exchangeDeclare(1, x, "topic");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        public void run() {
            long start = System.currentTimeMillis();
            int msg = 0;
            while (true) {
                try {
                    send(c, x);
                    msg++;
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    long now = System.currentTimeMillis();
                    System.err.println("Producer msg/sec: " + msg / ((now - start)/1000 + 1));
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void send(Channel channel, String x) throws IOException {
        byte[] payload = (System.nanoTime() + "-").getBytes();
        Random ran = new Random();
        String b = bindings[ran.nextInt(bindings.length -1 )];
        String r = b.replace("*", System.currentTimeMillis() + "");
        channel.basicPublish(1, x, r, MessageProperties.MINIMAL_BASIC, payload);
    }

}
