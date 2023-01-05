// Copyright (c) 2017-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.Connection;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtilsTest {

    @Test
    public void isVersion37orLater() {
        Map<String, Object> serverProperties = new HashMap<>();
        Connection connection = mock(Connection.class);
        when(connection.getServerProperties()).thenReturn(serverProperties);

        serverProperties.put("version", "3.7.0+rc.1.4.gedc5d96");
        Assertions.assertThat(TestUtils.isVersion37orLater(connection)).isTrue();

        serverProperties.put("version", "3.7.0~alpha.449-1");
        Assertions.assertThat(TestUtils.isVersion37orLater(connection)).isTrue();

        serverProperties.put("version", "3.7.1-alpha.40");
        Assertions.assertThat(TestUtils.isVersion37orLater(connection)).isTrue();
    }

    @Test
    public void isVersion38orLater() {
        Map<String, Object> serverProperties = new HashMap<>();
        Connection connection = mock(Connection.class);
        when(connection.getServerProperties()).thenReturn(serverProperties);

        serverProperties.put("version", "3.7.0+rc.1.4.gedc5d96");
        Assertions.assertThat(TestUtils.isVersion38orLater(connection)).isFalse();

        serverProperties.put("version", "3.7.0~alpha.449-1");
        Assertions.assertThat(TestUtils.isVersion38orLater(connection)).isFalse();

        serverProperties.put("version", "3.7.1-alpha.40");
        Assertions.assertThat(TestUtils.isVersion38orLater(connection)).isFalse();

        serverProperties.put("version", "3.8.0+beta.4.38.g33a7f97");
        Assertions.assertThat(TestUtils.isVersion38orLater(connection)).isTrue();
    }
}
