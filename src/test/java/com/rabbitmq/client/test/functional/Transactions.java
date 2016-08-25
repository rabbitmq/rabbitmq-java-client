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


package com.rabbitmq.client.test.functional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

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

    private long[] publishSelectAndGet(int n)
        throws IOException
    {
        for (int i = 0; i < n; i++) {
            basicPublish();
        }

        txSelect();

        long tags[] = new long[n];
        for (int i = 0; i < n; i++) {
            tags[i] = basicGet().getEnvelope().getDeliveryTag();
        }

        return tags;
    }

    /*
      publishes are embargoed until commit
     */
    @Test public void commitPublish()
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
    @Test public void rollbackPublish()
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
    @Test public void rollbackPublishOnClose()
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
    @Test public void requeueOnClose()
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
    @Test public void commitAcks()
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
    */
    @Test public void commitAcksOutOfOrder()
        throws IOException
    {
        long tags[] = publishSelectAndGet(4);
        channel.basicNack(tags[3], false, false);
        channel.basicNack(tags[2], false, false);
        channel.basicAck(tags[1], false);
        channel.basicAck(tags[0], false);
        txCommit();
    }

    /*
      rollback rolls back acks
      and a rolled back ack can be re-issued
    */
    @Test public void rollbackAcksAndReAck()
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
    @Test public void unknownTagAck()
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
    @Test public void noRequeueOnRollback()
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
    @Test public void autoAck()
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
    @Test public void ackAll()
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

    @Test public void nonTransactedCommit()
        throws IOException
    {
        try {
            txCommit();
            fail("Expected channel error");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void nonTransactedRollback()
        throws IOException
    {
        try {
            txRollback();
            fail("Expected channel error");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void redeliverAckedUncommitted()
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

    @Test public void commitWithDeletedQueue()
            throws IOException, TimeoutException {
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

    @Test public void shuffleAcksBeforeRollback()
        throws IOException
    {
        long tags[] = publishSelectAndGet(3);
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

    private final NackMethod basicNack = new NackMethod() {
            public void nack(long tag, boolean requeue)
                throws IOException
            {
                channel.basicNack(tag, false, requeue);
            }
        };

    private final NackMethod basicReject = new NackMethod() {
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
        long tags[] = publishSelectAndGet(3);
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

    @Test public void commitNacks()
        throws IOException
    {
        commitNacks(basicNack);
    }

    @Test public void rollbackNacks()
        throws IOException
    {
        rollbackNacks(basicNack);
    }

    @Test public void commitAcksAndNacks()
        throws IOException
    {
        commitAcksAndNacks(basicNack);
    }

    @Test public void commitRejects()
        throws IOException
    {
        commitNacks(basicReject);
    }

    @Test public void rollbackRejects()
        throws IOException
    {
        rollbackNacks(basicReject);
    }

    @Test public void commitAcksAndRejects()
        throws IOException
    {
        commitAcksAndNacks(basicReject);
    }
}
