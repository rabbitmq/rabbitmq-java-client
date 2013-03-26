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

package com.rabbitmq.examples;

import java.util.ArrayList;
import java.util.Collections;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

class ChannelCreationPerformance {
    static Connection connect() throws Exception{
      return new ConnectionFactory()
          {{setRequestedChannelMax(CHANNEL_MAX);}}.newConnection();
    }

    static int CHANNEL_MAX = 10000;
    static int STEP = 1000;
    static int START = STEP;

    abstract static class PerformanceTest{
      String name;
      Connection c;
      int i;

      PerformanceTest(String name){
        this.name = name;
      }

      void run() throws Exception{
        System.out.println(name);
        for(i = START; i <= CHANNEL_MAX ; i += STEP){
          c = connect();
          long start = System.currentTimeMillis();
          body();
          long time = System.currentTimeMillis() - start;
          System.out.println(i + "\t" + time + " (" + (1000 * i / ((double)time)) + " channels/s)");
          c.close();
        }
      }

      abstract void body() throws Exception;

    }

    public static void main(String[] args) throws Exception{
      new PerformanceTest("Sequential creation, no close:"){
        void body() throws Exception{
          for(int j = 1; j <= i; j++){
              c.createChannel();
          }
        }
      }.run();

      new PerformanceTest("Sequential creation followed by close:"){
        void body() throws Exception{
          for(int j = 1; j <= i; j++){
              c.createChannel().close();
          }
        }
      }.run();

      new PerformanceTest("Sequential creation then bulk close:"){
        void body() throws Exception{
          ArrayList<Channel> channels = new ArrayList<Channel>();
          for(int j = 1; j <= i; j++){
              channels.add(c.createChannel());
          }
          for(Channel chan : channels) chan.close();
        }
      }.run();

      new PerformanceTest("Sequential creation then out of order bulk close:"){
        void body() throws Exception{
          ArrayList<Channel> channels = new ArrayList<Channel>();
          for(int j = 1; j <= i; j++){
              channels.add(c.createChannel());
          }
          Collections.shuffle(channels);
          for(Channel chan : channels) chan.close();
        }
      }.run();
    }
}
