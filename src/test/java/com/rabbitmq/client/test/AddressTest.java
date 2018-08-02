// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.Address;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class AddressTest {

    @Test public void isHostWithPort() {
        assertTrue(Address.isHostWithPort("127.0.0.1:5672"));
        assertTrue(Address.isHostWithPort("[1080:0:0:0:8:800:200C:417A]:5672"));
        assertTrue(Address.isHostWithPort("[::1]:5672"));

        assertFalse(Address.isHostWithPort("127.0.0.1"));
        assertFalse(Address.isHostWithPort("[1080:0:0:0:8:800:200C:417A]"));
        assertFalse(Address.isHostWithPort("[::1]"));
    }

    @Test public void parseHost() {
        assertEquals("127.0.0.1", Address.parseHost("127.0.0.1:5672"));
        assertEquals("[1080:0:0:0:8:800:200C:417A]", Address.parseHost("[1080:0:0:0:8:800:200C:417A]:5673"));
        assertEquals("[::1]", Address.parseHost("[::1]:5672"));

        assertEquals("127.0.0.1", Address.parseHost("127.0.0.1"));
        assertEquals("[1080:0:0:0:8:800:200C:417A]", Address.parseHost("[1080:0:0:0:8:800:200C:417A]"));
        assertEquals("[::1]", Address.parseHost("[::1]"));
    }

    @Test public void parsePort() {
        assertEquals(5672, Address.parsePort("127.0.0.1:5672"));
        assertEquals(5673, Address.parsePort("[1080:0:0:0:8:800:200C:417A]:5673"));
        assertEquals(5672, Address.parsePort("[::1]:5672"));

        // "use default port" value
        assertEquals(-1, Address.parsePort("127.0.0.1"));
        assertEquals(-1, Address.parsePort("[1080:0:0:0:8:800:200C:417A]"));
        assertEquals(-1, Address.parsePort("[::1]"));
    }

    @Test public void parseIPv4() {
        assertEquals(addr("192.168.1.10"), Address.parseAddress("192.168.1.10"));
        assertEquals(addr("192.168.1.10", 5682), Address.parseAddress("192.168.1.10:5682"));
    }

    @Test public void parseIPv6() {
        // quoted IPv6 addresses without a port
        assertEquals(addr("[1080:0:0:0:8:800:200C:417A]"), Address.parseAddress("[1080:0:0:0:8:800:200C:417A]"));
        assertEquals(addr("[::1]"), Address.parseAddress("[::1]"));

        // quoted IPv6 addresses with a port
        assertEquals(addr("[1080:0:0:0:8:800:200C:417A]", 5673), Address.parseAddress("[1080:0:0:0:8:800:200C:417A]:5673"));
        assertEquals(addr("[::1]", 5673), Address.parseAddress("[::1]:5673"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnquotedIPv6() {
        // using a non-quoted IPv6 addresses with a port
        Address.parseAddress("::1:5673");
    }

    private Address addr(String addr) {
        return new Address(addr);
    }

    private Address addr(String addr, int port) {
        return new Address(addr, port);
    }

}
