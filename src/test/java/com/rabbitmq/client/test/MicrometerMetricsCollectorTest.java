// Copyright (c) 2018-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class MicrometerMetricsCollectorTest {

    SimpleMeterRegistry registry;

    MicrometerMetricsCollector collector;

    @BeforeEach
    public void init() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    public void noTag() {
        collector = new MicrometerMetricsCollector(registry, "rabbitmq");
        for (Meter meter : registry.getMeters()) {
            Assertions.assertThat(meter.getId().getTags()).isEmpty();
        }
    }

    @Test
    public void tags() {
        collector = new MicrometerMetricsCollector(registry, "rabbitmq", "uri", "/api/users");
        for (Meter meter : registry.getMeters()) {
            Assertions.assertThat(meter.getId().getTags()).hasSize(1);
        }
    }

    @Test
    public void tagsMustBeKeyValuePairs() {
        assertThatThrownBy(() -> new MicrometerMetricsCollector(registry, "rabbitmq", "uri"))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
