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

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.AMQP.BasicProperties;

public abstract class TransactionsBase
    extends BrokerTestCase
{

    protected static final String Q = "Transactions";

    protected long latestTag = 0L;

    protected void setUp()
        throws IOException
    {
        openConnection();
        openChannel();
        channel.queueDeclare(ticket, Q);
        closeChannel();
    }

    protected void tearDown()
        throws IOException
    {
        openChannel();
        channel.queueDelete(ticket, Q);
        closeChannel();
        closeConnection();
    }

    protected abstract BasicProperties getMessageProperties();

    private void channelOpen()
        throws IOException
    {
        openChannel();
    }

    private void channelClose()
        throws IOException
    {
        closeChannel();
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
        channel.basicPublish(ticket, "", Q, getMessageProperties(),
                             "Tx message".getBytes());
    }

    private GetResponse basicGet(boolean noAck)
        throws IOException
    {
        GetResponse r = channel.basicGet(ticket, Q, noAck);
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
        channelOpen();
        txSelect();
        basicPublish();
        assertNull(basicGet());
        txCommit();
        assertNotNull(basicGet());
        txCommit();
        channelClose();
    }

    /*
      rollback rolls back publishes
    */
    public void testRollbackPublish()
        throws IOException
    {
        channelOpen();
        txSelect();
        basicPublish();
        txRollback();
        assertNull(basicGet());
        channelClose();
    }

    /*
      closing a channel rolls back publishes
    */
    public void testRollbackPublishOnClose()
        throws IOException
    {
        channelOpen();
        txSelect();
        basicPublish();
        channelClose();
        channelOpen();
        assertNull(basicGet());
        channelClose();
    }

    /*
      closing a channel requeues both ack'ed and un-ack'ed messages
    */
    public void testRequeueOnClose()
        throws IOException
    {
        channelOpen();
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        channelClose();
        channelOpen();
        assertNotNull(basicGet());
        basicAck();
        assertNotNull(basicGet());
        basicAck();
        channelClose();
    }

    /*
      messages with committed acks are not requeued on channel close,
      messages that weren't ack'ed are requeued on close, but not before then.
    */
    public void testCommitAcks()
        throws IOException
    {
        channelOpen();
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        txCommit();
        assertNull(basicGet());
        channelClose();
        channelOpen();
        assertNotNull(basicGet());
        basicAck();
        assertNull(basicGet());
        channelClose();
    }

    /*
      rollback rolls back acks
      and a rolled back ack can be re-issued
    */
    public void testRollbackAcksAndReAck()
        throws IOException
    {
        channelOpen();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        txRollback();
        basicAck();
        txRollback();
        channelClose();
        channelOpen();
        assertNotNull(basicGet());
        basicAck();
        channelClose();
    }

    /*
      it is legal to ack the same message twice
    */
    public void testDuplicateAck()
        throws IOException
    {
        channelOpen();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicAck();
        txCommit();
        closeChannel();
    }

    /*
      it is illegal to ack with an unknown delivery tag
    */
    public void testUnknownTagAck()
        throws IOException
    {
        channelOpen();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicAck(latestTag+1, true);
        try {
            txCommit();
            fail("expected exception");
        } catch (IOException e) {}
        connection = null;
        openConnection();
    }

    /*
      rollback does not requeue delivered ack'ed or un-ack'ed messages
    */
    public void testNoRequeueOnRollback()
        throws IOException
    {
        channelOpen();
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        txRollback();
        assertNull(basicGet());
        channelClose();
    }

    /*
      auto-acks are not part of tx
    */
    public void testAutoAck()
        throws IOException
    {
        channelOpen();
        basicPublish();
        txSelect();
        basicGet(true);
        channelClose();
        channelOpen();
        assertNull(basicGet());
        channelClose();
    }

    /*
      "ack all", once committed, acks all delivered messages
    */
    public void testAckAll()
        throws IOException
    {
        channelOpen();
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicGet();
        basicAck(0L, true);
        txCommit();
        channelClose();
        channelOpen();
        assertNull(basicGet());
        channelClose();
    }

}
