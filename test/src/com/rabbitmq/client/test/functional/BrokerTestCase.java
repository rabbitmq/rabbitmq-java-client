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
//   The Initial Developers of the Original Code are LShift Ltd.,
//   Cohesive Financial Technologies LLC., and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd., Cohesive Financial Technologies
//   LLC., and Rabbit Technologies Ltd. are Copyright (C) 2007-2008
//   LShift Ltd., Cohesive Financial Technologies LLC., and Rabbit
//   Technologies Ltd.;
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import junit.framework.TestCase;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.tools.Host;

public class BrokerTestCase extends TestCase
{
    public ConnectionFactory connectionFactory = new ConnectionFactory();

    public Connection connection;
    public Channel channel;
    public int ticket;

    public void openConnection()
        throws IOException
    {
        if (connection == null) {
            connection = connectionFactory.newConnection("localhost");
        }
    }

    public void closeConnection()
        throws IOException
    {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public void openChannel()
        throws IOException
    {
        channel = connection.createChannel();
        ticket = channel.accessRequest("/data");
    }

    public void closeChannel()
        throws IOException
    {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    public void restart()
        throws IOException
    {
        tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-on-node");
        setUp();
    }

    public void forceSnapshot()
        throws IOException, InterruptedException
    {
        Host.executeCommand("cd ../rabbitmq-test; make force-snapshot");
    }

    protected void setUp()
        throws IOException
    {
        openConnection();
        openChannel();
    }

    protected void tearDown()
        throws IOException
    {
        closeChannel();
        closeConnection();
    }

}
