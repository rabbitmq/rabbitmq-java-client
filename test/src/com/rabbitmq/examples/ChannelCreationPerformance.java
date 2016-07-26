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

    static final int CHANNEL_MAX = 10000;
    static final int STEP = 1000;
    static final int START = STEP;

    abstract static class PerformanceTest{
      final String name;
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
