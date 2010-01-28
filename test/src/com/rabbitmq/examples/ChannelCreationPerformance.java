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

package com.rabbitmq.examples;

import com.rabbitmq.client.*;
import java.util.*;

public class ChannelCreationPerformance {
    static Connection connect() throws Exception{
      return new ConnectionFactory(new ConnectionParameters(){{
        setRequestedChannelMax(CHANNEL_MAX);
      }}).newConnection("localhost");
    }

    static int CHANNEL_MAX = 10000;
    static int STEP = 1000;
    static int START = STEP;

    public static void main(String[] args) throws Exception{
      System.out.println("Sequential creation, no close:"); 
      for(int i = START; i <= CHANNEL_MAX ; i += STEP){
        Connection c = connect();
        long start = System.currentTimeMillis(); 
        for(int j = 1; j <= i; j++){
            c.createChannel();
        } 
        System.out.println(i + "\t" + (System.currentTimeMillis() - start));
        c.close();
      }

      System.out.println("Sequential creation followed by close:"); 
      for(int i = START; i <= CHANNEL_MAX ; i += STEP){
        Connection c = connect();
        long start = System.currentTimeMillis(); 
        for(int j = 1; j <= i; j++){
            c.createChannel().close();
        } 
        System.out.println(i + "\t" + (System.currentTimeMillis() - start));
        c.close();
      }

      System.out.println("Sequential creation then bulk close:"); 
      for(int i = START; i <= CHANNEL_MAX ; i += STEP){
        Connection c = connect();
        long start = System.currentTimeMillis(); 
        ArrayList<Channel> channels = new ArrayList<Channel>();
        for(int j = 1; j <= i; j++){
            channels.add(c.createChannel());
        } 
        for(Channel chan : channels) chan.close();
        System.out.println(i + "\t" + (System.currentTimeMillis() - start));
        c.close();
      }
      System.out.println("Sequential creation then out of order bulk close:"); 
      for(int i = START; i <= CHANNEL_MAX ; i += STEP){
        Connection c = connect();
        long start = System.currentTimeMillis(); 
        ArrayList<Channel> channels = new ArrayList<Channel>();
        for(int j = 1; j <= i; j++){
            channels.add(c.createChannel());
        } 
        Collections.shuffle(channels);
        for(Channel chan : channels) chan.close();
        System.out.println(i + "\t" + (System.currentTimeMillis() - start));
        c.close();
      }
    }
}
