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


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;

import com.rabbitmq.client.GetResponse;

public class Transactions extends BrokerTestCase
{

    protected static final String Q = "Transactions";

    protected long latestTag = 0L;

    protected void createResources() throws IOException {
        channel.queueDeclare(Q, false, false, false, null);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
    }

    private void txSelect()
        throws IOException
    {
        channel.txSelect();
    }

    private void txCommit()
        throws IOException
    {
        channel.txCommit();
    }

    private void txRollback()
        throws IOException
    {
        channel.txRollback();
    }

    private void basicPublish()
        throws IOException
    {
        channel.basicPublish("", Q, null, "Tx message".getBytes());
    }

    private GetResponse basicGet(boolean noAck)
        throws IOException
    {
        GetResponse r = channel.basicGet(Q, noAck);
        latestTag = (r == null) ? 0L : r.getEnvelope().getDeliveryTag();
        return r;
    }

    private GetResponse basicGet()
        throws IOException
    {
        return basicGet(false);
    }

    private void basicAck(long tag, boolean multiple)
        throws IOException
    {
        channel.basicAck(tag, multiple);
    }

    private void basicAck()
        throws IOException
    {
        basicAck(latestTag, false);
    }

    /*
      publishes are embargoed until commit
     */
    public void testCommitPublish()
        throws IOException
    {
        txSelect();
        basicPublish();
        assertNull(basicGet());
        txCommit();
        assertNotNull(basicGet());
        txCommit();
    }

    /*
      rollback rolls back publishes
    */
    public void testRollbackPublish()
        throws IOException
    {
        txSelect();
        basicPublish();
        txRollback();
        assertNull(basicGet());
    }

    /*
      closing a channel rolls back publishes
    */
    public void testRollbackPublishOnClose()
        throws IOException
    {
        txSelect();
        basicPublish();
        closeChannel();
        openChannel();
        assertNull(basicGet());
    }

    /*
      closing a channel requeues both ack'ed and un-ack'ed messages
    */
    public void testRequeueOnClose()
        throws IOException
    {
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        closeChannel();
        openChannel();
        assertNotNull(basicGet());
        basicAck();
        assertNotNull(basicGet());
        basicAck();
    }

    /*
      messages with committed acks are not requeued on channel close,
      messages that weren't ack'ed are requeued on close, but not before then.
    */
    public void testCommitAcks()
        throws IOException
    {
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        txCommit();
        assertNull(basicGet());
        closeChannel();
        openChannel();
        assertNotNull(basicGet());
        basicAck();
        assertNull(basicGet());
    }

    /*
      rollback rolls back acks
      and a rolled back ack can be re-issued
    */
    public void testRollbackAcksAndReAck()
        throws IOException
    {
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        txRollback();
        basicAck();
        txRollback();
        closeChannel();
        openChannel();
        assertNotNull(basicGet());
        basicAck();
    }

    /*
      it is illegal to ack with an unknown delivery tag
    */
    public void testUnknownTagAck()
        throws IOException
    {
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicAck(latestTag+1, true);
        // "On a transacted channel, this check MUST be done immediately and
        // not delayed until a Tx.Commit."
        expectError(AMQP.PRECONDITION_FAILED);
    }

    /*
      rollback does not requeue delivered ack'ed or un-ack'ed messages
    */
    public void testNoRequeueOnRollback()
        throws IOException
    {
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        txRollback();
        assertNull(basicGet());
    }

    /*
      auto-acks are not part of tx
    */
    public void testAutoAck()
        throws IOException
    {
        basicPublish();
        txSelect();
        basicGet(true);
        closeChannel();
        openChannel();
        assertNull(basicGet());
    }

    /*
      "ack all", once committed, acks all delivered messages
    */
    public void testAckAll()
        throws IOException
    {
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicGet();
        basicAck(0L, true);
        txCommit();
        closeChannel();
        openChannel();
        assertNull(basicGet());
    }

    public void testNonTransactedCommit()
        throws IOException
    {
        try {
            txCommit();
            fail("Expected channel error");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testNonTransactedRollback()
        throws IOException
    {
        try {
            txRollback();
            fail("Expected channel error");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testRedeliverAckedUncommitted()
        throws IOException
    {
        txSelect();
        basicPublish();
        txCommit();
        basicGet();
        // Ack the message but do not commit the channel. The message
        // should not get redelivered (see
        // https://bugzilla.rabbitmq.com/show_bug.cgi?id=21845#c3)
        basicAck();
        channel.basicRecover(true);

        assertNull("Acked uncommitted message redelivered",
                   basicGet(true));
    }

    public void testCommitWithDeletedQueue()
        throws IOException
    {
        txSelect();
        basicPublish();
        releaseResources();
        try {
            txCommit();
        } catch (IOException e) {
            closeConnection();
            openConnection();
            openChannel();
            fail("commit failed");
        } finally {
            createResources(); // To allow teardown to function cleanly
        }
    }

    public void testShuffleAcksBeforeRollback()
        throws IOException
    {
        for (int i = 0; i < 3; i++) {
            basicPublish();
        }
        txSelect();
        long tags[] = new long[3];
        for (int i = 0; i < 3; i++) {
            tags[i] = basicGet().getEnvelope().getDeliveryTag();
        }
        basicAck(tags[2], false);
        basicAck(tags[1], false);
        txRollback();
        basicAck(tags[0], true);
        basicAck(tags[1], false);
        basicAck(tags[2], false);
        txCommit();
    }

    private abstract class NackMethod {
        abstract public void nack(long tag, boolean requeue)
            throws IOException;

        public void nack(boolean requeue)
            throws IOException
        {
            nack(latestTag, requeue);
        }

        public void nack()
            throws IOException
        {
            nack(latestTag, true);
        }
    }

    private NackMethod basicNack = new NackMethod() {
            public void nack(long tag, boolean requeue)
                throws IOException
            {
                channel.basicNack(tag, false, requeue);
            }
        };

    private NackMethod basicReject = new NackMethod() {
            public void nack(long tag, boolean requeue)
                throws IOException
            {
                channel.basicReject(tag, requeue);
            }
        };

    /*
      messages with nacks get requeued after the transaction commit.
      messages with nacks with requeue = false are not requeued.
    */
    public void commitNacks(NackMethod method)
        throws IOException
    {
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        method.nack();
        basicGet();
        method.nack(false);
        assertNull(basicGet());
        txCommit();
        assertNotNull(basicGet());
        assertNull(basicGet());
    }

    public void rollbackNacks(NackMethod method)
        throws IOException
    {
        basicPublish();
        txSelect();
        basicGet();
        method.nack(true);
        txRollback();
        assertNull(basicGet());
    }

    public void commitAcksAndNacks(NackMethod method)
        throws IOException
    {
        for (int i = 0; i < 3; i++) {
            basicPublish();
        }
        txSelect();
        long tags[] = new long[3];
        for (int i = 0; i < 3; i++) {
            tags[i] = basicGet().getEnvelope().getDeliveryTag();
        }
        basicAck(tags[1], false);
        basicAck(tags[0], false);
        method.nack(tags[2], false);
        txRollback();
        basicAck(tags[2], false);
        method.nack(tags[0], true);
        method.nack(tags[1], false);
        txCommit();
        assertNotNull(basicGet());
        assertNull(basicGet());
    }

    public void testCommitNacks()
        throws IOException
    {
        commitNacks(basicNack);
    }

    public void testRollbackNacks()
        throws IOException
    {
        rollbackNacks(basicNack);
    }

    public void testCommitAcksAndNacks()
        throws IOException
    {
        commitAcksAndNacks(basicNack);
    }

    public void testCommitRejects()
        throws IOException
    {
        commitNacks(basicReject);
    }

    public void testRollbackRejects()
        throws IOException
    {
        rollbackNacks(basicReject);
    }

    public void testCommitAcksAndRejects()
        throws IOException
    {
        commitAcksAndNacks(basicReject);
    }
}
