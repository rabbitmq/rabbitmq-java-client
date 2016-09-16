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

package com.rabbitmq.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AddressResolver} that resolves DNS record IPs.
 * Uses {@link InetAddress} internally.
 * The first returned address is used when automatic recovery is NOT enabled
 * at the {@link ConnectionFactory} level.
 * When automatic recovery is enabled, a random address will be picked up
 * from the returned list of {@link Address}es.
 */
public class DnsRecordIpAddressResolver implements AddressResolver {

    private final Address address;

    private final boolean ssl;

    public DnsRecordIpAddressResolver(String hostname, int port, boolean ssl) {
        this(new Address(hostname, port), ssl);
    }

    public DnsRecordIpAddressResolver(String hostname, int port) {
        this(new Address(hostname, port), false);
    }

    public DnsRecordIpAddressResolver() {
        this("localhost");
    }

    public DnsRecordIpAddressResolver(String hostname) {
        this(new Address(hostname), false);
    }

    public DnsRecordIpAddressResolver(Address address) {
        this(address, false);
    }

    public DnsRecordIpAddressResolver(Address address, boolean ssl) {
        this.address = address;
        this.ssl = ssl;
    }

    /**
     * Get the IP addresses from a DNS query
     * @return candidate {@link Address}es
     * @throws IOException if DNS resolution fails
     */
    @Override
    public List<Address> getAddresses() throws UnknownHostException {
        String hostName = address.getHost();
        int portNumber = ConnectionFactory.portOrDefault(address.getPort(), ssl);

        InetAddress[] inetAddresses = resolveIpAddresses(hostName);

        List<Address> addresses = new ArrayList<Address>();
        for (InetAddress inetAddress : inetAddresses) {
            addresses.add(new Address(inetAddress.getHostAddress(), portNumber));
        }
        return addresses;
    }

    protected InetAddress[] resolveIpAddresses(String hostName) throws UnknownHostException {
        return InetAddress.getAllByName(hostName);
    }

}
