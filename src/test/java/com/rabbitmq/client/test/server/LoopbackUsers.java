// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.test.server;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.test.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.tools.Host;

public class LoopbackUsers {
    
	@BeforeEach public void setUp() throws IOException {
        Host.rabbitmqctl("add_user test test");
        Host.rabbitmqctl("set_permissions test '.*' '.*' '.*'");
    }

    @AfterEach public void tearDown() throws IOException {
        Host.rabbitmqctl("delete_user test");
    }

    @Test public void loopback() throws IOException, TimeoutException {
	      if (!Host.isOnDocker()) {
            String addr = findRealIPAddress().getHostAddress();
            assertGuestFail(addr);
            Host.rabbitmqctl("eval 'application:set_env(rabbit, loopback_users, []).'");
            assertGuestSucceed(addr);
            Host.rabbitmqctl("eval 'application:set_env(rabbit, loopback_users, [<<\"guest\">>]).'");
            assertGuestFail(addr);
        }
    }

    private void assertGuestSucceed(String addr) throws IOException, TimeoutException {
        succeedConnect("guest", addr);
        succeedConnect("guest", "localhost");
        succeedConnect("test", addr);
        succeedConnect("test", "localhost");
    }

    private void assertGuestFail(String addr) throws IOException, TimeoutException {
        failConnect("guest", addr);
        succeedConnect("guest", "localhost");
        succeedConnect("test", addr);
        succeedConnect("test", "localhost");
    }

    private void succeedConnect(String name, String addr) throws IOException, TimeoutException {
        getFactory(name, addr).newConnection().close();
    }

    private void failConnect(String name, String addr) throws IOException, TimeoutException {
        try {
            getFactory(name, addr).newConnection();
            fail();
        }
        catch (AuthenticationFailureException e) {
            // success
        }
    }

    private ConnectionFactory getFactory(String name, String addr) {
        ConnectionFactory factory = TestUtils.connectionFactory();
        factory.setUsername(name);
        factory.setPassword(name);
        factory.setHost(addr);
        return factory;
    }

    // Find the first IP address of a network interface that is up, not loopback, not point to point (e.g. VPN thing)
    private static InetAddress findRealIPAddress() throws SocketException {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface nif = ifs.nextElement();
            if (nif.isUp() && !nif.isPointToPoint() && !nif.isLoopback() && !nif.isVirtual()) {
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr;
                    }
                }
            }
        }
        throw new RuntimeException("Could not determine real network address");
    }
}
