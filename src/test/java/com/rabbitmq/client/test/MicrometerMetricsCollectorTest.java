// Copyright (c) 2018-Present Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.test;

import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class MicrometerMetricsCollectorTest {

    SimpleMeterRegistry registry;

    MicrometerMetricsCollector collector;

    @Before
    public void init() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    public void noTag() {
        collector = new MicrometerMetricsCollector(registry, "rabbitmq");
        for (Meter meter : registry.getMeters()) {
            assertThat(size(meter.getId().getTags()), is(0));
        }
    }

    @Test
    public void tags() {
        collector = new MicrometerMetricsCollector(registry, "rabbitmq", "uri", "/api/users");
        for (Meter meter : registry.getMeters()) {
            assertThat(size(meter.getId().getTags()), is(1));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void tagsMustBeKeyValuePairs() {
        collector = new MicrometerMetricsCollector(registry, "rabbitmq", "uri");
    }

    static int size(Iterable<?> iterable) {
        Iterator<?> iterator = iterable.iterator();
        int i = 0;
        for ( ; iterator.hasNext() ; ++i ) iterator.next();
        return i;
    }

}
