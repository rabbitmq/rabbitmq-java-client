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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

public class CcRoutes extends BrokerTestCase  {

    private String[] queues;
    private final String exDirect = "direct_cc_exchange";
    private final String exTopic = "topic_cc_exchange";
    private BasicProperties.Builder propsBuilder;
    protected Map<String, Object> headers;
    private List<String> ccList;
    private List<String> bccList;

    @Override public void setUp() throws IOException, TimeoutException {
        super.setUp();
        propsBuilder = new BasicProperties.Builder();
        headers = new HashMap<>();
        ccList = new ArrayList<>();
        bccList = new ArrayList<>();
    }

    @Override protected void createResources() throws IOException, TimeoutException {
        super.createResources();
        queues = IntStream.range(1, 4)
                .mapToObj(index -> CcRoutes.class.getSimpleName() + "." + UUID.randomUUID().toString())
                .collect(Collectors.toList())
                .toArray(new String[]{});
        for (String q : queues) {
            channel.queueDeclare(q, false, false, true, null);
        }
        channel.exchangeDeclare(exDirect, "direct", false, true, null);
        channel.exchangeDeclare(exTopic, "topic", false, true, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        super.releaseResources();
        for (String q : queues) {
            channel.queueDelete(q);
        }
    }

    @Test public void ccList() throws IOException {
        ccList.add(queue2());
        ccList.add(queue3());
        headerPublish("", queue1(), ccList, null);
        expect(new String []{queue1(), queue2(), queue3()}, true);
     }

    @Test public void ccIgnoreEmptyAndInvalidRoutes() throws IOException {
        bccList.add("frob");
        headerPublish("", queue1(), ccList, bccList);
        expect(new String []{queue1()}, true);
     }

    @Test public void bcc() throws IOException {
        bccList.add(queue2());
        headerPublish("", queue1(), null, bccList);
        expect(new String []{queue1(), queue2()}, false);
     }

    @Test public void noDuplicates() throws IOException {
        ccList.add(queue1());
        ccList.add(queue1());
        bccList.add(queue1());
        headerPublish("", queue1(), ccList, bccList);
        expect(new String[] {queue1()}, true);
     }

    @Test public void directExchangeWithoutBindings() throws IOException {
        ccList.add(queue1());
        headerPublish(exDirect, queue2(), ccList, null);
        expect(new String[] {}, true);
    }

    @Test public void topicExchange() throws IOException {
        ccList.add("routing_key");
        channel.queueBind(queue2(), exTopic, "routing_key");
        headerPublish(exTopic, "", ccList, null);
        expect(new String[] {queue2()}, true);
    }

    @Test public void boundExchanges() throws IOException {
        ccList.add("routing_key1");
        bccList.add("routing_key2");
        channel.exchangeBind(exTopic, exDirect, "routing_key1");
        channel.queueBind(queue2(), exTopic, "routing_key2");
        headerPublish(exDirect, "", ccList, bccList);
        expect(new String[] {queue2()}, true);
    }

    @Test public void nonArray() throws IOException {
        headers.put("CC", 0);
        propsBuilder.headers(headers);
        channel.basicPublish("", queue1(), propsBuilder.build(), new byte[0]);
        try {
            expect(new String[] {}, false);
            fail();
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    private void headerPublish(String ex, String to, List<String> cc, List<String> bcc) throws IOException {
        if (cc != null) {
            headers.put("CC", ccList);
        }
        if (bcc != null) {
            headers.put("BCC", bccList);
        }
        propsBuilder.headers(headers);
        channel.basicPublish(ex, to, propsBuilder.build(), new byte[0]);
    }

    private void expect(String[] expectedQueues, boolean usedCc) throws IOException {
        GetResponse getResponse;
        List<String> expectedList = Arrays.asList(expectedQueues);
        for (String q : queues) {
            getResponse = basicGet(q);
            if (expectedList.contains(q)) {
                assertNotNull(getResponse);
                assertEquals(0, getResponse.getMessageCount());
                Map<?, ?> headers = getResponse.getProps().getHeaders();
                if (headers != null){
                    assertEquals(usedCc, headers.containsKey("CC"));
                    assertFalse(headers.containsKey("BCC"));
                }
            } else {
                assertNull(getResponse);
            }
        }
    }

    String queue1() {
        return queues[0];
    }

    String queue2() {
        return queues[1];
    }

    String queue3() {
        return queues[2];
    }
}
