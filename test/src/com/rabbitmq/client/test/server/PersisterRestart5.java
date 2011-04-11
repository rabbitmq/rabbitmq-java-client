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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class PersisterRestart5 extends BrokerTestCase {

    private static final String Q1 = "Restart5One";
    private static final String Q2 = "Restart5Two";
    private static final String X = "Exchange5";


    protected void exercisePersister() 
      throws IOException
    {
        basicPublishPersistent(X, "foo.foo");
        basicPublishVolatile(X, "foo.qux");
    }

    public void testRestart()
        throws IOException, InterruptedException
    {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q1, X, "foo.*");
        declareAndBindDurableQueue(Q2, X, "foo.*");
        channel.txSelect();
        exercisePersister();
        channel.txCommit();
        exercisePersister();
        // Delivering messages which are in the snapshot
        channel.txCommit();
        // Those will be in the incremental snapshot then
        exercisePersister();
        // but removed
        channel.txRollback();
        // Those will be in the incremental snapshot then
        exercisePersister();
        // and hopefully delivered
        channel.txCommit();

        restart();
        
        assertDelivered(Q1, 3);
        assertDelivered(Q2, 3);
        deleteQueue(Q2);
        deleteQueue(Q1);
        deleteExchange(X);
    }

}
