// Copyright (c) 2026 Broadcom. All Rights Reserved.
// The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.ServerCapability;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AMQConnectionCapabilitiesTest {

    @Test
    void capabilitySetToTrueIsPresent() {
        assertThat(AMQConnection.doesServerHaveCapability(
            Map.of("capabilities", Map.of("accept_consumer_cancel_ok", true)),
            ServerCapability.ACCEPT_CONSUMER_CANCEL_OK)).isTrue();
    }

    @Test
    void capabilitySetToFalseIsAbsent() {
        assertThat(AMQConnection.doesServerHaveCapability(
            Map.of("capabilities", Map.of("accept_consumer_cancel_ok", false)),
            ServerCapability.ACCEPT_CONSUMER_CANCEL_OK)).isFalse();
    }

    @Test
    void missingCapabilityIsAbsent() {
        assertThat(AMQConnection.doesServerHaveCapability(
            Map.of("capabilities", Map.of("consumer_cancel_notify", true)),
            ServerCapability.ACCEPT_CONSUMER_CANCEL_OK)).isFalse();
    }

    @Test
    void capabilityIsAbsentWithoutCapabilitiesTable() {
        assertThat(AMQConnection.doesServerHaveCapability(Map.of(), ServerCapability.ACCEPT_CONSUMER_CANCEL_OK))
            .isFalse();
    }
}
