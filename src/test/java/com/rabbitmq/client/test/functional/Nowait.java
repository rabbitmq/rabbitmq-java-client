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

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class Nowait extends BrokerTestCase {
    public void testQueueDeclareWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNoWait(q, false, true, true, null);
        channel.queueDeclarePassive(q);
    }

    public void testQueueBindWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNoWait(q, false, true, true, null);
        channel.queueBindNoWait(q, "amq.fanout", "", null);
    }

    public void testExchangeDeclareWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
            channel.exchangeDeclarePassive(x);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testExchangeBindWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
            channel.exchangeBindNoWait(x, "amq.fanout", "", null);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testExchangeUnbindWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclare(x, "fanout", false, false, false, null);
            channel.exchangeBind(x, "amq.fanout", "", null);
            channel.exchangeUnbindNoWait(x, "amq.fanout", "", null);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testQueueDeleteWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNoWait(q, false, true, true, null);
        channel.queueDeleteNoWait(q, false, false);
    }

    public void testExchangeDeleteWithNowait() throws IOException {
        String x = generateExchangeName();
        channel.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
        channel.exchangeDeleteNoWait(x, false);
    }
}
