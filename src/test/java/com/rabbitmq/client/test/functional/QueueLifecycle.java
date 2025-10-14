// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.test.BrokerTestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test queue auto-delete and exclusive semantics.
 */
public class QueueLifecycle extends BrokerTestCase {

    void verifyQueueExists(String name) throws IOException {
        channel.queueDeclarePassive(name);
    }

    void verifyQueueMissing(String name) {
      waitAtMost(
          () -> {
            Channel ch = connection.createChannel();
            boolean result;
            // the queue should be gone, declare-passive should fail
            try {
              ch.queueDeclarePassive(name);
              ch.close();
              result = false;
            } catch (IOException e) {
              result = true;
            }
            return result;
          });
    }

    /**
     * Verify that a queue both exists and has the properties as given
     *
     * @throws IOException
     *             if one of these conditions is not true
     */
    void verifyQueue(String name, boolean durable, boolean exclusive,
            boolean autoDelete, Map<String, Object> args) throws IOException {
        verifyQueueExists(name);
        // use passive/equivalent rule to check that it has the same properties
        channel.queueDeclare(name, durable, exclusive, autoDelete, args);
    }

    // NB the exception will close the connection
    void verifyNotEquivalent(boolean durable, boolean exclusive,
            boolean autoDelete) throws IOException {
        String q = "queue";
        channel.queueDeclare(q, false, false, false, null);
        try {
            verifyQueue(q, durable, exclusive, autoDelete, null);
        } catch (IOException ioe) {
            if (exclusive)
                checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            else
                checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
            return;
        }
        fail("Queue.declare should have been rejected as not equivalent");
    }

    /** From amqp-0-9-1.xml, for "passive" property, "equivalent" rule:
     * "If not set and the queue exists, the server MUST check that the
     * existing queue has the same values for durable, exclusive,
     * auto-delete, and arguments fields. The server MUST respond with
     * Declare-Ok if the requested queue matches these fields, and MUST
     * raise a channel exception if not."
     */
    @Test public void queueEquivalence() throws IOException {
        String q = "queue";
        channel.queueDeclare(q, false, false, false, null);
        // equivalent
        verifyQueue(q, false, false, false, null);

        // the spec says that the arguments table is matched on
        // being semantically equivalent.
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put("assumed-to-be-semantically-void", "bar");
        verifyQueue(q, false, false, false, args);

    }

    // not equivalent in various ways
    @Test public void queueNonEquivalenceDurable() throws IOException {
        verifyNotEquivalent(true, false, false);
    }

    @Test public void queueNonEquivalenceExclusive() throws IOException {
        verifyNotEquivalent(false, true, false);
    }

    @Test public void queueNonEquivalenceAutoDelete() throws IOException {
        verifyNotEquivalent(false, false, true);
    }

    // Note that this assumes that auto-deletion is synchronous with
    // basic.cancel,
    // which is not actually in the spec. (If it isn't, there's a race here).
    @Test public void queueAutoDelete() throws IOException {
        String name = "tempqueue";
        channel.queueDeclare(name, false, false, true, null);
        // now it's there
        verifyQueue(name, false, false, true, null);
        Consumer consumer = new DefaultConsumer(channel);
        String consumerTag = channel.basicConsume(name, consumer);
        channel.basicCancel(consumerTag);
        // now it's not .. we hope
        try {
            verifyQueueExists(name);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_FOUND, ioe);
            return;
        }
        fail("Queue should have been auto-deleted after we removed its only consumer");
    }

    @Test public void exclusiveNotAutoDelete() throws IOException {
        String name = generateQueueName();
        channel.queueDeclare(name, false, true, false, null);
        // now it's there
        verifyQueue(name, false, true, false, null);
        Consumer consumer = new DefaultConsumer(channel);
        String consumerTag = channel.basicConsume(name, consumer);
        channel.basicCancel(consumerTag);
        // and still there, because exclusive no longer implies autodelete
        verifyQueueExists(name);
    }

    @Test public void exclusiveGoesWithConnection() throws IOException, TimeoutException {
        String name = generateQueueName();
        channel.queueDeclare(name, false, true, false, null);
        // now it's there
        verifyQueue(name, false, true, false, null);
        closeConnection();
        openConnection();
        openChannel();
        verifyQueueMissing(name);
    }

    @Test public void argumentArrays() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        String[] arr = new String[]{"foo", "bar", "baz"};
        args.put("my-key", arr);
        String queueName = "argumentArraysQueue";
        channel.queueDeclare(queueName, true, true, false, args);
        verifyQueue(queueName, true, true, false, args);
    }

    @Test public void queueNamesLongerThan255Characters() throws IOException {
        String q = new String(new byte[300]).replace('\u0000', 'x');
        try {
            channel.queueDeclare(q, false, false, false, null);
            fail("queueDeclare should have failed");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    @Test public void singleLineFeedStrippedFromQueueName() throws IOException {
        channel.queueDeclare("que\nue_test", false, false, true, null);
        verifyQueue(NAME_STRIPPED, false, false, true, null);
    }

    @Test public void multipleLineFeedsStrippedFromQueueName() throws IOException {
        channel.queueDeclare("que\nue_\ntest\n", false, false, true, null);
        verifyQueue(NAME_STRIPPED, false, false, true, null);
    }

    @Test public void multipleLineFeedAndCarriageReturnsStrippedFromQueueName() throws IOException {
        channel.queueDeclare("q\ru\ne\r\nue_\ntest\n\r", false, false, true, null);
        verifyQueue(NAME_STRIPPED, false, false, true, null);
    }

    static final String NAME_STRIPPED = "queue_test";

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(NAME_STRIPPED);
    }
}
