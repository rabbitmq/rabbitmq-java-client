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

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.AlreadyClosedException;
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
        super.setUp();
        closeChannel();
    }

    protected void createResources() throws IOException {
        channel.queueDeclare(Q);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
    }

    protected abstract BasicProperties getMessageProperties();

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
        channel.basicPublish("", Q, getMessageProperties(),
                             "Tx message".getBytes());
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
        openChannel();
        txSelect();
        basicPublish();
        assertNull(basicGet());
        txCommit();
        assertNotNull(basicGet());
        txCommit();
        closeChannel();
    }

    /*
      rollback rolls back publishes
    */
    public void testRollbackPublish()
        throws IOException
    {
        openChannel();
        txSelect();
        basicPublish();
        txRollback();
        assertNull(basicGet());
        closeChannel();
    }

    /*
      closing a channel rolls back publishes
    */
    public void testRollbackPublishOnClose()
        throws IOException
    {
        openChannel();
        txSelect();
        basicPublish();
        closeChannel();
        openChannel();
        assertNull(basicGet());
        closeChannel();
    }

    /*
      closing a channel requeues both ack'ed and un-ack'ed messages
    */
    public void testRequeueOnClose()
        throws IOException
    {
        openChannel();
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
        closeChannel();
    }

    /*
      messages with committed acks are not requeued on channel close,
      messages that weren't ack'ed are requeued on close, but not before then.
    */
    public void testCommitAcks()
        throws IOException
    {
        openChannel();
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
        closeChannel();
    }

    /*
      rollback rolls back acks
      and a rolled back ack can be re-issued
    */
    public void testRollbackAcksAndReAck()
        throws IOException
    {
        openChannel();
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
        closeChannel();
    }

    /*
      it is legal to ack the same message twice
    */
    public void testDuplicateAck()
        throws IOException
    {
        openChannel();
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
        openChannel();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicAck(latestTag+1, true);
        try {
            txCommit();
            fail("expected exception");
        }
        catch (IOException e) {}
        catch (AlreadyClosedException e) {}
        connection = null;
        openConnection();
    }

    /*
      rollback does not requeue delivered ack'ed or un-ack'ed messages
    */
    public void testNoRequeueOnRollback()
        throws IOException
    {
        openChannel();
        basicPublish();
        basicPublish();
        txSelect();
        basicGet();
        basicAck();
        basicGet();
        txRollback();
        assertNull(basicGet());
        closeChannel();
    }

    /*
      auto-acks are not part of tx
    */
    public void testAutoAck()
        throws IOException
    {
        openChannel();
        basicPublish();
        txSelect();
        basicGet(true);
        closeChannel();
        openChannel();
        assertNull(basicGet());
        closeChannel();
    }

    /*
      "ack all", once committed, acks all delivered messages
    */
    public void testAckAll()
        throws IOException
    {
        openChannel();
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
        closeChannel();
    }

}
