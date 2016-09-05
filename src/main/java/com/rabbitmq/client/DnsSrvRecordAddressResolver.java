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

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

/**
 * {@link AddressResolver} that resolves addresses against a DNS SRV request.
 * SRV records contain the hostname and the port for a given service.
 * They also support priorities, to give precedence to a given host
 * over other hosts.
 * Note the hosts returned by the SRV query must be resolvable by
 * the DNS servers of the underlying platform (or the default ones
 * specified for this Java process). This class does not issue a
 * query for A records after the SRV query.
 * This implementation returns the highest-priority records first.
 * This behavior can be changed by overriding the {@code sort} method.
 *
 * This implementation uses internally the {@code com.sun.jndi.dns.DnsContextFactory}
 * class for the DNS query.
 *
 * The first returned address is used when automatic recovery is NOT enabled
 * at the {@link ConnectionFactory} level.
 * When automatic recovery is enabled, a random address will be picked up
 * from the returned list of {@link Address}es.
 *
 */
public class DnsSrvRecordAddressResolver implements AddressResolver {

    /**
     * the SRV service information.
     * e.g. _sip._tcp.example.com or rabbitmq.service.consul
     */
    private final String service;

    /**
     * URLs of the DNS servers.
     * e.g. dns://server1.example.com/example.com
     * Default to {@code dns:}, that is the DNS server of the
     * underlying platform.
     */
    private final String dnsUrls;

    public DnsSrvRecordAddressResolver(String service) {
        this(service, "dns:");
    }

    public DnsSrvRecordAddressResolver(String service, String dnsUrls) {
        this.service = service;
        this.dnsUrls = dnsUrls;
    }

    @Override
    public List<Address> getAddresses() throws IOException {
        List<SrvRecord> records = lookupSrvRecords(service, dnsUrls);
        records = sort(records);

        List<Address> addresses = new ArrayList<Address>();
        for (SrvRecord record : records) {
            addresses.add(new Address(record.getHost(), record.getPort()));
        }

        return addresses;
    }

    protected List<SrvRecord> lookupSrvRecords(String service, String dnsUrls) throws IOException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", dnsUrls);

        List<SrvRecord> records = new ArrayList<SrvRecord>();
        try {
            DirContext ctx = new InitialDirContext(env);
            Attributes attributes = ctx.getAttributes(service, new String[] { "SRV" });
            NamingEnumeration<?> servers = attributes.get("srv").getAll();
            while (servers.hasMore()) {
                records.add(mapSrvRecord((String) servers.next()));
            }
        } catch(NamingException e) {
            throw new IOException("Error during DNS SRV query", e);
        }

        return records;
    }

    protected SrvRecord mapSrvRecord(String srvResult) {
        return SrvRecord.fromSrvQueryResult(srvResult);
    }

    protected List<SrvRecord> sort(List<SrvRecord> records) {
        Collections.sort(records);
        return records;
    }

    public static class SrvRecord implements Comparable<SrvRecord> {

        private final int priority;
        private final int weight;
        private final int port;
        private final String host;

        public SrvRecord(int priority, int weight, int port, String host) {
            this.priority = priority;
            this.weight = weight;
            this.port = port;
            int lastDotIndex = host.lastIndexOf(".");
            if(lastDotIndex > 0) {
                this.host = host.substring(0, lastDotIndex);
            } else {
                this.host = host;
            }
        }

        public int getPriority() {
            return priority;
        }

        public int getWeight() {
            return weight;
        }

        public int getPort() {
            return port;
        }

        public String getHost() {
            return host;
        }

        public static SrvRecord fromSrvQueryResult(String srvResult) {
            String[] fields = srvResult.split(" ");
            return new SrvRecord(
                Integer.parseInt(fields[0]),
                Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]),
                fields[3]
            );
        }

        @Override
        public int compareTo(SrvRecord o) {
            return (this.priority < o.getPriority()) ? -1 : ((this.priority == o.getPriority()) ? 0 : 1);
        }
    }
}
