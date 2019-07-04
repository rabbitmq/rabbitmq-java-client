// Copyright (c) 2017-Present Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.Connection;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtilsTest {

    @Test
    public void isVersion37orLater() {
        Map<String, Object> serverProperties = new HashMap<>();
        Connection connection = mock(Connection.class);
        when(connection.getServerProperties()).thenReturn(serverProperties);

        serverProperties.put("version", "3.7.0+rc.1.4.gedc5d96");
        assertThat(TestUtils.isVersion37orLater(connection), is(true));

        serverProperties.put("version", "3.7.0~alpha.449-1");
        assertThat(TestUtils.isVersion37orLater(connection), is(true));

        serverProperties.put("version", "3.7.1-alpha.40");
        assertThat(TestUtils.isVersion37orLater(connection), is(true));
    }

    @Test
    public void isVersion38orLater() {
        Map<String, Object> serverProperties = new HashMap<>();
        Connection connection = mock(Connection.class);
        when(connection.getServerProperties()).thenReturn(serverProperties);

        serverProperties.put("version", "3.7.0+rc.1.4.gedc5d96");
        assertThat(TestUtils.isVersion38orLater(connection), is(false));

        serverProperties.put("version", "3.7.0~alpha.449-1");
        assertThat(TestUtils.isVersion38orLater(connection), is(false));

        serverProperties.put("version", "3.7.1-alpha.40");
        assertThat(TestUtils.isVersion38orLater(connection), is(false));

        serverProperties.put("version", "3.8.0+beta.4.38.g33a7f97");
        assertThat(TestUtils.isVersion38orLater(connection), is(true));
    }
}
